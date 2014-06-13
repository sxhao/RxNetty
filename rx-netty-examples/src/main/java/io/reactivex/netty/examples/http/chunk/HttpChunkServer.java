/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.reactivex.netty.examples.http.chunk;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Func1;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * @author Tomasz Bak
 */
public class HttpChunkServer {
    static final int DEFAULT_PORT = 8103;

    private int port;
    private String textFile;

    public HttpChunkServer(int port, String textFile) {
        this.port = port;
        this.textFile = textFile;
    }

    public HttpServer<ByteBuf, ByteBuf> createServer() {
        HttpServer<ByteBuf, ByteBuf> server = RxNetty.createHttpServer(port, new RequestHandler<ByteBuf, ByteBuf>() {
            @Override
            public Observable<Void> handle(HttpServerRequest<ByteBuf> request, final HttpServerResponse<ByteBuf> response) {
                try {
                    final Reader fileReader = new BufferedReader(new FileReader(textFile));
                    return createReaderObserver(response, fileReader).finallyDo(new Action0() {
                        @Override
                        public void call() {
                            try {
                                fileReader.close();
                            } catch (IOException e) {
                                // IGNORE
                            }
                        }
                    });
                } catch (IOException e) {
                    return Observable.error(e);
                }
            }
        });
        System.out.println("HTTP chunk server started...");
        return server;
    }

    private Observable<Void> createReaderObserver(final HttpServerResponse<ByteBuf> response, final Reader reader) throws IOException {
        Iterable<String> fileIterable = new Iterable<String>() {
            private char[] charBuf = new char[16];

            private int lastCount = 0;

            @Override
            public Iterator<String> iterator() {

                return new Iterator<String>() {
                    @Override
                    public boolean hasNext() {
                        try {
                            return (lastCount > 0) || (lastCount = reader.read(charBuf)) > 0;
                        } catch (IOException e) {
                            lastCount = 0;
                            return false;
                        }
                    }

                    @Override
                    public String next() {
                        if (hasNext()) {
                            String next = new String(charBuf, 0, lastCount);
                            lastCount = 0;
                            return next;
                        }
                        throw new NoSuchElementException("no more data to return");
                    }

                    @Override
                    public void remove() {
                        // IGNORE
                    }
                };
            }
        };
        return Observable.from(fileIterable).flatMap(new Func1<String, Observable<Void>>() {
            @Override
            public Observable<Void> call(String text) {
                return response.writeStringAndFlush(text);
            }
        });
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("ERROR: give text file name");
            return;
        }
        String textFile = args[0];
        new HttpChunkServer(DEFAULT_PORT, textFile).createServer().startAndWait();
    }
}
