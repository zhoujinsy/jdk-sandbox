/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.incubator.http.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import jdk.incubator.http.HttpResponse.BodySubscriber;
import jdk.incubator.http.internal.common.MinimalFuture;
import jdk.incubator.http.internal.common.Utils;
import static java.nio.charset.StandardCharsets.UTF_8;

public class ResponseSubscribers {

    public static class ConsumerSubscriber implements BodySubscriber<Void> {
        private final Consumer<Optional<byte[]>> consumer;
        private Flow.Subscription subscription;
        private final CompletableFuture<Void> result = new MinimalFuture<>();
        private final AtomicBoolean subscribed = new AtomicBoolean();

        public ConsumerSubscriber(Consumer<Optional<byte[]>> consumer) {
            this.consumer = Objects.requireNonNull(consumer);
        }

        @Override
        public CompletionStage<Void> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (!subscribed.compareAndSet(false, true)) {
                subscription.cancel();
            } else {
                this.subscription = subscription;
                subscription.request(1);
            }
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            for (ByteBuffer item : items) {
                byte[] buf = new byte[item.remaining()];
                item.get(buf);
                consumer.accept(Optional.of(buf));
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            consumer.accept(Optional.empty());
            result.complete(null);
        }

    }

    public static class PathSubscriber implements BodySubscriber<Path> {

        private final Path file;
        private final CompletableFuture<Path> result = new MinimalFuture<>();

        private volatile Flow.Subscription subscription;
        private volatile FileChannel out;
        private volatile AccessControlContext acc;
        private final OpenOption[] options;

        public PathSubscriber(Path file, OpenOption... options) {
            this.file = file;
            this.options = options;
        }

        public void setAccessControlContext(AccessControlContext acc) {
            this.acc = acc;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (System.getSecurityManager() != null && acc == null)
                throw new InternalError(
                        "Unexpected null acc when security manager has been installed");

            this.subscription = subscription;
            try {
                PrivilegedExceptionAction<FileChannel> pa =
                        () -> FileChannel.open(file, options);
                out = AccessController.doPrivileged(pa, acc);
            } catch (PrivilegedActionException pae) {
                Throwable t = pae.getCause() != null ? pae.getCause() : pae;
                result.completeExceptionally(t);
                subscription.cancel();
                return;
            }
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            try {
                out.write(items.toArray(Utils.EMPTY_BB_ARRAY));
            } catch (IOException ex) {
                Utils.close(out);
                subscription.cancel();
                result.completeExceptionally(ex);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable e) {
            result.completeExceptionally(e);
            Utils.close(out);
        }

        @Override
        public void onComplete() {
            Utils.close(out);
            result.complete(file);
        }

        @Override
        public CompletionStage<Path> getBody() {
            return result;
        }
    }

    public static class ByteArraySubscriber<T> implements BodySubscriber<T> {
        private final Function<byte[], T> finisher;
        private final CompletableFuture<T> result = new MinimalFuture<>();
        private final List<ByteBuffer> received = new ArrayList<>();

        private volatile Flow.Subscription subscription;

        public ByteArraySubscriber(Function<byte[],T> finisher) {
            this.finisher = finisher;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) {
                subscription.cancel();
                return;
            }
            this.subscription = subscription;
            // We can handle whatever you've got
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            // incoming buffers are allocated by http client internally,
            // and won't be used anywhere except this place.
            // So it's free simply to store them for further processing.
            assert Utils.hasRemaining(items);
            received.addAll(items);
        }

        @Override
        public void onError(Throwable throwable) {
            received.clear();
            result.completeExceptionally(throwable);
        }

        static private byte[] join(List<ByteBuffer> bytes) {
            int size = Utils.remaining(bytes, Integer.MAX_VALUE);
            byte[] res = new byte[size];
            int from = 0;
            for (ByteBuffer b : bytes) {
                int l = b.remaining();
                b.get(res, from, l);
                from += l;
            }
            return res;
        }

        @Override
        public void onComplete() {
            try {
                result.complete(finisher.apply(join(received)));
                received.clear();
            } catch (IllegalArgumentException e) {
                result.completeExceptionally(e);
            }
        }

        @Override
        public CompletionStage<T> getBody() {
            return result;
        }
    }

    /**
     * An InputStream built on top of the Flow API.
     */
    public static class HttpResponseInputStream extends InputStream
        implements BodySubscriber<InputStream>
    {
        final static boolean DEBUG = Utils.DEBUG;
        final static int MAX_BUFFERS_IN_QUEUE = 1;  // lock-step with the producer

        // An immutable ByteBuffer sentinel to mark that the last byte was received.
        private static final ByteBuffer LAST_BUFFER = ByteBuffer.wrap(new byte[0]);
        private static final List<ByteBuffer> LAST_LIST = List.of(LAST_BUFFER);
        private static final System.Logger DEBUG_LOGGER =
                Utils.getDebugLogger("HttpResponseInputStream"::toString, DEBUG);

        // A queue of yet unprocessed ByteBuffers received from the flow API.
        private final BlockingQueue<List<ByteBuffer>> buffers;
        private volatile Flow.Subscription subscription;
        private volatile boolean closed;
        private volatile Throwable failed;
        private volatile Iterator<ByteBuffer> currentListItr;
        private volatile ByteBuffer currentBuffer;
        private final AtomicBoolean subscribed = new AtomicBoolean();

        public HttpResponseInputStream() {
            this(MAX_BUFFERS_IN_QUEUE);
        }

        HttpResponseInputStream(int maxBuffers) {
            int capacity = (maxBuffers <= 0 ? MAX_BUFFERS_IN_QUEUE : maxBuffers);
            // 1 additional slot needed for LAST_LIST added by onComplete
            this.buffers = new ArrayBlockingQueue<>(capacity + 1);
        }

        @Override
        public CompletionStage<InputStream> getBody() {
            // Returns the stream immediately, before the
            // response body is received.
            // This makes it possible for sendAsync().get().body()
            // to complete before the response body is received.
            return CompletableFuture.completedStage(this);
        }

        // Returns the current byte buffer to read from.
        // If the current buffer has no remaining data, this method will take the
        // next buffer from the buffers queue, possibly blocking until
        // a new buffer is made available through the Flow API, or the
        // end of the flow has been reached.
        private ByteBuffer current() throws IOException {
            while (currentBuffer == null || !currentBuffer.hasRemaining()) {
                // Check whether the stream is closed or exhausted
                if (closed || failed != null) {
                    throw new IOException("closed", failed);
                }
                if (currentBuffer == LAST_BUFFER) break;

                try {
                    if (currentListItr == null || !currentListItr.hasNext()) {
                        // Take a new list of buffers from the queue, blocking
                        // if none is available yet...

                        DEBUG_LOGGER.log(Level.DEBUG, "Taking list of Buffers");
                        List<ByteBuffer> lb = buffers.take();
                        currentListItr = lb.iterator();
                        DEBUG_LOGGER.log(Level.DEBUG, "List of Buffers Taken");

                        // Check whether an exception was encountered upstream
                        if (closed || failed != null)
                            throw new IOException("closed", failed);

                        // Check whether we're done.
                        if (lb == LAST_LIST) {
                            currentListItr = null;
                            currentBuffer = LAST_BUFFER;
                            break;
                        }

                        // Request another upstream item ( list of buffers )
                        Flow.Subscription s = subscription;
                        if (s != null) {
                            DEBUG_LOGGER.log(Level.DEBUG, "Increased demand by 1");
                            s.request(1);
                        }
                        assert currentListItr != null;
                        if (lb.isEmpty()) continue;
                    }
                    assert currentListItr != null;
                    assert currentListItr.hasNext();
                    DEBUG_LOGGER.log(Level.DEBUG, "Next Buffer");
                    currentBuffer = currentListItr.next();
                } catch (InterruptedException ex) {
                    // continue
                }
            }
            assert currentBuffer == LAST_BUFFER || currentBuffer.hasRemaining();
            return currentBuffer;
        }

        @Override
        public int read(byte[] bytes, int off, int len) throws IOException {
            // get the buffer to read from, possibly blocking if
            // none is available
            ByteBuffer buffer;
            if ((buffer = current()) == LAST_BUFFER) return -1;

            // don't attempt to read more than what is available
            // in the current buffer.
            int read = Math.min(buffer.remaining(), len);
            assert read > 0 && read <= buffer.remaining();

            // buffer.get() will do the boundary check for us.
            buffer.get(bytes, off, read);
            return read;
        }

        @Override
        public int read() throws IOException {
            ByteBuffer buffer;
            if ((buffer = current()) == LAST_BUFFER) return -1;
            return buffer.get() & 0xFF;
        }

        @Override
        public void onSubscribe(Flow.Subscription s) {
            try {
                if (!subscribed.compareAndSet(false, true)) {
                    s.cancel();
                } else {
                    // check whether the stream is already closed.
                    // if so, we should cancel the subscription
                    // immediately.
                    boolean closed;
                    synchronized (this) {
                        closed = this.closed;
                        if (!closed) {
                            this.subscription = s;
                        }
                    }
                    if (closed) {
                        s.cancel();
                        return;
                    }
                    assert buffers.remainingCapacity() > 1; // should contain at least 2
                    DEBUG_LOGGER.log(Level.DEBUG, () -> "onSubscribe: requesting "
                            + Math.max(1, buffers.remainingCapacity() - 1));
                    s.request(Math.max(1, buffers.remainingCapacity() - 1));
                }
            } catch (Throwable t) {
                failed = t;
                try {
                    close();
                } catch (IOException x) {
                    // OK
                } finally {
                    onError(t);
                }
            }
        }

        @Override
        public void onNext(List<ByteBuffer> t) {
            Objects.requireNonNull(t);
            try {
                DEBUG_LOGGER.log(Level.DEBUG, "next item received");
                if (!buffers.offer(t)) {
                    throw new IllegalStateException("queue is full");
                }
                DEBUG_LOGGER.log(Level.DEBUG, "item offered");
            } catch (Throwable ex) {
                failed = ex;
                try {
                    close();
                } catch (IOException ex1) {
                    // OK
                } finally {
                    onError(ex);
                }
            }
        }

        @Override
        public void onError(Throwable thrwbl) {
            subscription = null;
            failed = Objects.requireNonNull(thrwbl);
            // The client process that reads the input stream might
            // be blocked in queue.take().
            // Tries to offer LAST_LIST to the queue. If the queue is
            // full we don't care if we can't insert this buffer, as
            // the client can't be blocked in queue.take() in that case.
            // Adding LAST_LIST to the queue is harmless, as the client
            // should find failed != null before handling LAST_LIST.
            buffers.offer(LAST_LIST);
        }

        @Override
        public void onComplete() {
            subscription = null;
            onNext(LAST_LIST);
        }

        @Override
        public void close() throws IOException {
            Flow.Subscription s;
            synchronized (this) {
                if (closed) return;
                closed = true;
                s = subscription;
                subscription = null;
            }
            // s will be null if already completed
            try {
                if (s != null) {
                    s.cancel();
                }
            } finally {
                buffers.offer(LAST_LIST);
                super.close();
            }
        }

    }

    public static BodySubscriber<Stream<String>> createLineStream() {
        return createLineStream(UTF_8);
    }

    public static BodySubscriber<Stream<String>> createLineStream(Charset charset) {
        Objects.requireNonNull(charset);
        BodySubscriber<InputStream> s = new HttpResponseInputStream();
        return new MappedSubscriber<InputStream,Stream<String>>(s,
            (InputStream stream) -> {
                return new BufferedReader(new InputStreamReader(stream, charset))
                            .lines().onClose(() -> Utils.close(stream));
            });
    }

    /**
     * Currently this consumes all of the data and ignores it
     */
    public static class NullSubscriber<T> implements BodySubscriber<T> {

        private final CompletableFuture<T> cf = new MinimalFuture<>();
        private final Optional<T> result;
        private final AtomicBoolean subscribed = new AtomicBoolean();

        public NullSubscriber(Optional<T> result) {
            this.result = result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (!subscribed.compareAndSet(false, true)) {
                subscription.cancel();
            } else {
                subscription.request(Long.MAX_VALUE);
            }
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            Objects.requireNonNull(items);
        }

        @Override
        public void onError(Throwable throwable) {
            cf.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            if (result.isPresent()) {
                cf.complete(result.get());
            } else {
                cf.complete(null);
            }
        }

        @Override
        public CompletionStage<T> getBody() {
            return cf;
        }
    }

    /** An adapter between {@code BodySubscriber} and {@code Flow.Subscriber}. */
    public static final class SubscriberAdapter<S extends Subscriber<? super List<ByteBuffer>>,R>
        implements BodySubscriber<R>
    {
        private final CompletableFuture<R> cf = new MinimalFuture<>();
        private final S subscriber;
        private final Function<S,R> finisher;
        private volatile Subscription subscription;

        public SubscriberAdapter(S subscriber, Function<S,R> finisher) {
            this.subscriber = Objects.requireNonNull(subscriber);
            this.finisher = Objects.requireNonNull(finisher);
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            Objects.requireNonNull(subscription);
            if (this.subscription != null) {
                subscription.cancel();
            } else {
                this.subscription = subscription;
                subscriber.onSubscribe(subscription);
            }
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            Objects.requireNonNull(item);
            try {
                subscriber.onNext(item);
            } catch (Throwable throwable) {
                subscription.cancel();
                onError(throwable);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            Objects.requireNonNull(throwable);
            try {
                subscriber.onError(throwable);
            } finally {
                cf.completeExceptionally(throwable);
            }
        }

        @Override
        public void onComplete() {
            try {
                subscriber.onComplete();
            } finally {
                try {
                    cf.complete(finisher.apply(subscriber));
                } catch (Throwable throwable) {
                    cf.completeExceptionally(throwable);
                }
            }
        }

        @Override
        public CompletionStage<R> getBody() {
            return cf;
        }
    }

    /**
     * A body subscriber which receives input from an upstream subscriber
     * and maps that subscriber's body type to a new type. The upstream subscriber
     * delegates all flow operations directly to this object. The
     * {@link CompletionStage} returned by {@link #getBody()}} takes the output
     * of the upstream {@code getBody()} and applies the mapper function to
     * obtain the new {@code CompletionStage} type.
     *
     * Uses an Executor that must be set externally.
     *
     * @param <T> the upstream body type
     * @param <U> this subscriber's body type
     */
    public static class MappedSubscriber<T,U> implements BodySubscriber<U> {
        final BodySubscriber<T> upstream;
        final Function<T,U> mapper;

        /**
         *
         * @param upstream
         * @param mapper
         */
        public MappedSubscriber(BodySubscriber<T> upstream, Function<T,U> mapper) {
            this.upstream = upstream;
            this.mapper = mapper;
        }

        @Override
        public CompletionStage<U> getBody() {
            return upstream.getBody()
                    .thenApply(mapper);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            upstream.onSubscribe(subscription);
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            upstream.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            upstream.onError(throwable);
        }

        @Override
        public void onComplete() {
            upstream.onComplete();
        }
    }
}
