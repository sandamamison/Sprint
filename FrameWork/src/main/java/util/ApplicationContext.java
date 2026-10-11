package util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class ApplicationContext {
    private final Map<String, Object> registry = new HashMap<>();

    public void initialize() {
        System.out.println("[MonFramework] Initialisation des composants...");

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            String url = "jdbc:mysql://127.0.0.1:3306/notes"
                    + "?useSSL=false&serverTimezone=UTC";
            String user = "root";
            String password = "112345677";
            Connection connection = DriverManager.getConnection(url, user, password);

            registry.put("databaseConnection", connection);
            System.out.println("[MonFramework] Connexion MySQL etablie et enregistree !");

        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Pilote JDBC MySQL introuvable !", e);
        } catch (SQLException e) {
            throw new RuntimeException("Impossible de se connecter a MySQL !", e);
        }
    }

    public Object getBean(String name) {
        return registry.get(name);
    }

    public void close() {
        System.out.println("[MonFramework] Fermeture des connexions...");
        Connection conn = (Connection) registry.get("databaseConnection");
        if (conn != null) {
            try {
                conn.close();
                System.out.println("[MonFramework] Connexion BDD fermée.");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}