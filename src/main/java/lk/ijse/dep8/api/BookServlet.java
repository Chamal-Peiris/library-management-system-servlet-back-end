package lk.ijse.dep8.api;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;
import lk.ijse.dep8.dto.BookDTO;
import lk.ijse.dep8.dto.MemberDTO;
import lk.ijse.dep8.exception.ValidationException;

import javax.annotation.Resource;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@WebServlet(name = "BookServlet", value = {"/v1/books/*"})
public class BookServlet extends HttpServlet {
    @Resource(name = "java:comp/env/jdbc/pool4library")
    private volatile DataSource pool;

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req.getPathInfo() == null || req.getPathInfo().equals("/")) {
            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Unable to delete all books");
            return;
        } else if (req.getPathInfo() != null && !req.getPathInfo().substring(1).matches("\\d{9}")) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid members");
            return;
        }
        String isbn = req.getPathInfo().replaceAll("[/]", "");

        try (Connection connection = pool.getConnection()) {
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM book WHERE isbn=?");
            stm.setString(1, isbn);
            ResultSet rst = stm.executeQuery();
            if (rst.next()) {
                stm = connection.prepareStatement("DELETE FROM book WHERE isbn =?");
                stm.setString(1, isbn);
                if (stm.executeUpdate() != 1) {
                    throw new RuntimeException("Failed to delete member");
                }
                resp.sendError(HttpServletResponse.SC_NO_CONTENT);
            } else {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Member Not found");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req.getPathInfo() != null && !req.getPathInfo().equals("/")) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String query = req.getParameter("q");
        query = "%" + ((query == null) ? "" : query) + "%";

        try (Connection connection = pool.getConnection()) {

            boolean pagination = req.getParameter("page") != null &&
                    req.getParameter("size") != null;
            String sql = null;

            if (pagination) {
                sql = "SELECT * FROM book WHERE isbn LIKE ? OR name LIKE ? OR author LIKE ? LIMIT ? OFFSET ?";
            } else {
                sql = "SELECT * FROM book WHERE isbn LIKE ? OR name LIKE ? OR author LIKE ?";
            }

            PreparedStatement stm = connection.prepareStatement(sql);
            PreparedStatement stmCount = connection.prepareStatement("SELECT count(*) FROM book WHERE isbn LIKE ? OR name LIKE ? OR author LIKE ?");

            stm.setString(1, query);
            stm.setString(2, query);
            stm.setString(3, query);
            stmCount.setString(1, query);
            stmCount.setString(2, query);
            stmCount.setString(3, query);

            if (pagination){
                int page = Integer.parseInt(req.getParameter("page"));
                int size = Integer.parseInt(req.getParameter("size"));
                stm.setInt(4, size);
                stm.setInt(5, (page - 1) * size);
            }
            ResultSet rst = stm.executeQuery();

            List<BookDTO> books = new ArrayList<>();

            while (rst.next()) {
                books.add((new BookDTO(
                        rst.getString("isbn"),
                        rst.getString("name"),
                        rst.getString("author")
                )));
            }

            resp.setContentType("application/json");

            if (!pagination) {
                resp.setHeader("X-Count", books.size() + "");
            }else{
                ResultSet rst2 = stmCount.executeQuery();
                if (rst2.next()){
                    resp.setHeader("X-Count", rst2.getString(1));
                }
            }

            Jsonb jsonb = JsonbBuilder.create();
            jsonb.toJson(books, resp.getWriter());

        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        doSaveOrUpdate(request,response);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doSaveOrUpdate(req,resp);
    }

    private void doSaveOrUpdate(HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (req.getContentType() == null ||
                !req.getContentType().toLowerCase().startsWith("application/json")) {
            res.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }

        String method = req.getMethod();
        String pathInfo = req.getPathInfo();

        if (method.equals("POST") &&
                !((req.getServletPath().equalsIgnoreCase("/books") ||
                        req.getServletPath().equalsIgnoreCase("/books/")))) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        } else if (method.equals("PUT") && !(pathInfo != null &&
                pathInfo.substring(1).matches("\\d{9}[/]?"))) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND, "Book does not exist");
            return;
        }

        try {
            Jsonb jsonb = JsonbBuilder.create();
            BookDTO book = jsonb.fromJson(req.getReader(), BookDTO.class);
            if (method.equals("POST") &&
                    (book.getIsbn() == null || !book.getIsbn().matches("\\d{9}"))) {
                throw new ValidationException("Invalid ISBN");
            } else if (book.getName() == null || !book.getName().matches("[A-Za-z ]+")) {
                throw new ValidationException("Invalid Name");
            } else if (book.getAuthor() == null || !book.getAuthor().matches("[A-Za-z ]+")) {
                throw new ValidationException("Invalid  Author Name");
            }

            if (method.equals("PUT")) {
                book.setIsbn(pathInfo.replaceAll("[/]", ""));
            }

            try (Connection connection = pool.getConnection()) {
                PreparedStatement stm = connection.prepareStatement("SELECT * FROM book WHERE isbn=?");
                stm.setString(1, book.getIsbn());
                ResultSet rst = stm.executeQuery();

                if (rst.next()) {
                    if (method.equals("POST")) {
                        res.sendError(HttpServletResponse.SC_CONFLICT, "Book already exists");
                    } else {
                        stm = connection.prepareStatement("UPDATE book SET name=?, author=? WHERE isbn=?");
                        stm.setString(1, book.getName());
                        stm.setString(2, book.getAuthor());
                        stm.setString(3, book.getIsbn());
                        if (stm.executeUpdate() != 1) {
                            throw new RuntimeException("Failed to update the book");
                        }
                        res.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    }
                } else {
                    stm = connection.prepareStatement("INSERT INTO book (isbn, name, author) VALUES (?,?,?)");
                    stm.setString(1, book.getIsbn());
                    stm.setString(2, book.getName());
                    stm.setString(3, book.getAuthor());
                    if (stm.executeUpdate() != 1) {
                        throw new RuntimeException("Failed to register the Book");
                    }
                    res.setStatus(HttpServletResponse.SC_CREATED);
                }
            }

        } catch (JsonbException | ValidationException e) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    (e instanceof JsonbException) ? "Invalid JSON" : e.getMessage());
        } catch (Throwable t) {
            t.printStackTrace();
            res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
