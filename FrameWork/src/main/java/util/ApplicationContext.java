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
            Class.forName("org.postgresql.Driver"); 

            String url = "jdbc:postgresql://localhost:5432/ma_base";
            String user = "postgres";
            String password = "password";
            Connection connection = DriverManager.getConnection(url, user, password);

            registry.put("databaseConnection", connection);
            System.out.println("[MonFramework] Connexion BDD établie et enregistrée !");

        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Pilote JDBC introuvable !", e);
        } catch (SQLException e) {
            throw new RuntimeException("Impossible de se connecter à la base de données !", e);
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