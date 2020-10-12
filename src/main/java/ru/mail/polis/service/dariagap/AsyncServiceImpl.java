package ru.mail.polis.service.dariagap;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mail.polis.dao.DAO;
import ru.mail.polis.service.Service;
import ru.mail.polis.util.Util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static one.nio.http.Request.METHOD_DELETE;
import static one.nio.http.Request.METHOD_GET;
import static one.nio.http.Request.METHOD_PUT;

public class AsyncServiceImpl extends HttpServer implements Service {
    @NotNull
    private final DAO dao;
    private final ExecutorService exec;
    private final Logger log = LoggerFactory.getLogger(ServiceImpl.class);

    public AsyncServiceImpl(final int port,
                            @NotNull final DAO dao,
                            @NotNull final int executors,
                            @NotNull final int queueSize) throws IOException {
        super(formConfig(port));
        this.dao = dao;
        this.exec = new ThreadPoolExecutor(
                executors,
                executors,
                20L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueSize),
                new ThreadFactoryBuilder()
                        .setNameFormat("Executor-%d")
                        .build()
                );
    }

    private static HttpServerConfig formConfig(final int port) {
        final HttpServerConfig conf = new HttpServerConfig();
        final AcceptorConfig ac = new AcceptorConfig();
        ac.port = port;
        conf.acceptors = new AcceptorConfig[]{ac};
        return conf;
    }

    @Path("/v0/status")
    public void status(HttpSession session) {
        Future<?> future = exec.submit(()-> {
            try {
                session.sendResponse(Response.ok("OK"));
            } catch (IOException ex) {
                log.error("Can not send response.", ex);
            }
        });

        if (future.isCancelled()) {
            log.error("Error in executor");
        }
    }

    /**
     * Get data by id.
     *
     * @param id key of entity
     * @return response OK with value or status BAD_REQUEST, INTERNAL_ERROR, NOT_FOUND
     */
    @Path("/v0/entity")
    @RequestMethod(METHOD_GET)
    public void get(@Param(value = "id", required = true) final String id,
                    HttpSession session) {
        Future<?> future = exec.submit(()-> {
            try {
                if (id.isEmpty()) {
                    session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                }

                try {
                    final ByteBuffer value = dao.get(ByteBuffer.wrap(id.getBytes(UTF_8)));
                    session.sendResponse(new Response(Response.OK, Util.byteBufferToBytes(value)));
                } catch (IOException ex) {
                    session.sendResponse(new Response(Response.INTERNAL_ERROR, Response.EMPTY));
                } catch (NoSuchElementException ex) {
                    session.sendResponse(new Response(Response.NOT_FOUND, Response.EMPTY));
                }
            } catch (IOException ex) {
                log.error("Can not send response.", ex);
            }
        });

        if (future.isCancelled()) {
            log.error("Error in executor");
        }
    }

    /**
     * Set data by id.
     *
     * @param id key of entity
     * @param request request with the entity value in body
     * @return status CREATED or status BAD_REQUEST, INTERNAL_ERROR
     */
    @Path("/v0/entity")
    @RequestMethod(METHOD_PUT)
    public void put(@Param("id") final String id,
                        @Param("request") final Request request,
                        HttpSession session) {
        Future<?> future = exec.submit(()-> {
            try {
                if (id.isEmpty()) {
                    session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                }

                try {
                    dao.upsert(ByteBuffer.wrap(id.getBytes(UTF_8)),
                            ByteBuffer.wrap(request.getBody()));
                    session.sendResponse(new Response(Response.CREATED, Response.EMPTY));
                } catch (IOException ex) {
                    session.sendResponse(new Response(Response.INTERNAL_ERROR, Response.EMPTY));
                }
            } catch (IOException ex) {
                log.error("Can not send response.", ex);
            }
        });

        if (future.isCancelled()) {
            log.error("Error in executor");
        }
    }

    /**
     * Delete data by id.
     *
     * @param id key of entity
     * @return status ACCEPTED or status BAD_REQUEST, INTERNAL_ERROR
     */
    @Path("/v0/entity")
    @RequestMethod(METHOD_DELETE)
    public void delete(@Param("id") final String id, HttpSession session) {
        Future<?> future = exec.submit(()-> {
            try {
                if (id.isEmpty()) {
                    session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                }

                try {
                    dao.remove(ByteBuffer.wrap(id.getBytes(UTF_8)));
                    session.sendResponse(new Response(Response.ACCEPTED, Response.EMPTY));
                } catch (IOException ex) {
                    session.sendResponse(new Response(Response.INTERNAL_ERROR, Response.EMPTY));
                }
            } catch (IOException ex) {
                log.error("Can not send response.", ex);
            }
        });

        if (future.isCancelled()) {
            log.error("Error in executor");
        }
    }

    @Override
    public void handleDefault(final Request request, final HttpSession session) throws IOException {
        Future<?> future = exec.submit(()-> {
            try {
                final Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
                session.sendResponse(response);
            } catch (IOException ex) {
                log.error("Can not send response.", ex);
            }
        });

        if (future.isCancelled()) {
            log.error("Error in executor");
        }
    }

    @Override
    public synchronized void stop() {
        super.stop();
        exec.shutdown();
        try {
            exec.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            log.error("Can not stop server.", ex);
        }
    }
}