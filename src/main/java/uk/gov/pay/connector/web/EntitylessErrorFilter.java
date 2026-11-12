package uk.gov.pay.connector.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * An error answer a resource gives without an entity (a status alone) is rendered as the servlet container's error
 * page for that status (see {@link ContainerErrorPages}), as it always has been.
 */
public class EntitylessErrorFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        BodyTrackingResponse tracked = new BodyTrackingResponse(response);
        chain.doFilter(request, tracked);
        if (!response.isCommitted() && response.getStatus() >= 400 && !tracked.bodyUsed) {
            response.sendError(response.getStatus());
        }
    }

    private static final class BodyTrackingResponse extends HttpServletResponseWrapper {

        private boolean bodyUsed;

        private BodyTrackingResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            bodyUsed = true;
            return super.getOutputStream();
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            bodyUsed = true;
            return super.getWriter();
        }

        @Override
        public void sendError(int sc) throws IOException {
            bodyUsed = true;
            super.sendError(sc);
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            bodyUsed = true;
            super.sendError(sc, msg);
        }
    }
}
