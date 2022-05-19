package lk.ijse.dep8.filter;

import javax.servlet.*;
import javax.servlet.annotation.*;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebFilter(filterName = "CorseFilter",urlPatterns = "/*")
public class CorseFilter extends HttpFilter {
    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException, ServletException {
        String origin=req.getHeader("Origin");
        if (origin != null && origin.contains(getServletContext().getInitParameter("origin"))){
            res.setHeader("Access-Control-Allow-Origin",origin);
            res.setHeader("Access-Control-Allow-Headers","Content-Type");
            res.setHeader("Access-Control-Expose-Headers","Content-Type,X-Count");
            if(req.getMethod().equals("OPTIONS")){
                res.setHeader("Access-Control-Allow-Methods","GET,POST,PUT,DELETE,HEAD");

            }
        }

        chain.doFilter(req,res);
    }
}
