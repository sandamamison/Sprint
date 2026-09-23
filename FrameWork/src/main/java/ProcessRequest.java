import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import util.MethodExecutor;
import util.ModelAndView;
import util.UrlMethod;

public class ProcessRequest extends HttpServlet {

    private HashMap<UrlMethod, Method> urlMap;
    private String prefix;
    private String surfix;

    @SuppressWarnings("unchecked")
    @Override
    public void init() throws ServletException {
        ServletContext context = getServletContext();
        this.urlMap = (HashMap<UrlMethod, Method>) context.getAttribute("urlMap");
        this.prefix = context.getInitParameter("view-prefix");
        this.surfix = context.getInitParameter("view-suffix");
    }

    protected void processRequest(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        String url = req.getRequestURI();
        String[] uri = url.split("/");
        String output = uri[uri.length - 1];

        if (output.endsWith(".html")) {
            String cheminPhysique = getServletContext().getRealPath("/" + output);
            File fichier = new File(cheminPhysique);

            if (fichier.exists()) {
                res.setContentType("text/html;charset=UTF-8");
                Files.copy(fichier.toPath(), res.getOutputStream());
                return;
            } else {
                res.sendError(404, "Fichier introuvable");
                return;
            }
        }

        String httpMethod = req.getMethod();
        UrlMethod cle = new UrlMethod("/" + output, httpMethod);

        Method method = (urlMap != null) ? urlMap.get(cle) : null;

        if (method == null) {
            res.setContentType("text/plain;charset=UTF-8");
            PrintWriter out = res.getWriter();
            out.println("Aucune methode trouvee pour l'URL : " + url + " et la Methode : " + httpMethod);
            out.println("");
            out.println("URLs disponibles :");

            if (urlMap != null) {
                for (UrlMethod u : urlMap.keySet()) {
                    Method m = urlMap.get(u);
                    out.println(u.getMethod() + " " + u.getUrl() + "    " + m.getDeclaringClass().getName() + "."
                            + m.getName() + "()");
                }
            }
            return;
        }

        try {
            Object obj = MethodExecutor.execute(method);

            if (obj instanceof ModelAndView) {
                ModelAndView mv = (ModelAndView) obj;
                Map<String, Object> map = mv.getModel();

                for (Map.Entry<String, Object> mm : map.entrySet()) {
                    req.setAttribute(mm.getKey(), mm.getValue());
                }
                String path = this.prefix + mv.getView() + this.surfix;
                RequestDispatcher requestDispatcher = req.getRequestDispatcher(path);
                requestDispatcher.forward(req, res);
                return;
            }

            res.setContentType("text/plain;charset=UTF-8");
            PrintWriter out = res.getWriter();
            out.println("URL     : " + url);
            out.println("Methode : " + method.getName() + "()");
            out.println("Execution de :" + method);
            out.println("Resultat : " + obj);

        } catch (Exception e) {
            res.setContentType("text/plain;charset=UTF-8");
            PrintWriter out = res.getWriter();
            out.println("Erreur lors de l'execution du methode :" + e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        processRequest(req, res);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        processRequest(req, res);
    }
}
