package util;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import annotation.Controller;
import annotation.GetMapping;
import annotation.PostMapping;
import annotation.UrlMapping;

public class FinderAnnotation {

    public static HashMap<UrlMethod, Method> getControleurMaping(String packageName) throws Exception {
        // recuperer les classes du package qui sont des controleurs
        List<Class<?>> listClasses = findAllControleur(packageName);
        HashMap<UrlMethod, Method> allMaping = new HashMap<>();

        // recuperer la liste des methodes de chaque controleur
        for (Class<?> controleur : listClasses) {
            System.out.println("Verification de la classe " + controleur.getName());
            for (Method m : controleur.getDeclaredMethods()) {

                UrlMethod urlMethod = null;

                System.out.println("Verification du method : " + m.getName());

                // recuperer l'annotation UrlMapping
                if (m.isAnnotationPresent(UrlMapping.class)) {
                    UrlMapping urlMap = m.getAnnotation(UrlMapping.class);
                    urlMethod = new UrlMethod(urlMap);
                }

                // recuperer l'annotation GetMapping
                if (m.isAnnotationPresent(GetMapping.class)) {
                    System.out.println("Anotation get present ");
                    GetMapping urlMap = m.getAnnotation(GetMapping.class);
                    urlMethod = new UrlMethod(urlMap);
                }

                // recuperer l'annotation PostMapping
                if (m.isAnnotationPresent(PostMapping.class)) {
                    PostMapping urlMap = m.getAnnotation(PostMapping.class);
                    urlMethod = new UrlMethod(urlMap);
                }

                if (urlMethod != null) {
                    if (allMaping.containsKey(urlMethod)) {
                        Method firstDefin = allMaping.get(urlMethod);
                        throw new IllegalArgumentException("L'url : " + urlMethod.getUrl()
                                + " est redefini .\nDefinition 1 : " + firstDefin.getDeclaringClass().getName()
                                + "::" + firstDefin.getName() + "\nDefinition 2 : "
                                + m.getDeclaringClass().getName() + "::" + m.getName());
                    }
                    allMaping.put(urlMethod, m);
                }
            }
        }
        return allMaping;
    }

    public static List<Class<?>> findAllControleur(String packageName) throws Exception {
        // recuperer les classes du package
        List<Class<?>> listClasses = getClassesInPackage(packageName);
        List<Class<?>> listControleurs = new ArrayList<>();
        // verifier si la classe possede l'annotation @Controller
        for (Class<?> class1 : listClasses) {
            if (class1.isAnnotationPresent(Controller.class)) {
                listControleurs.add(class1);
            }
        }
        return listControleurs;
    }

    public static List<String> findControleurName(String packageName) throws Exception {
        List<Class<?>> listClasses = findAllControleur(packageName);
        List<String> listeClasseAnnoter = new ArrayList<>();
        for (Class<?> class1 : listClasses) {
            listeClasseAnnoter.add(class1.getSimpleName());
        }
        return listeClasseAnnoter;
    }

    public static List<Class<?>> getClassesInPackage(String packageName) throws Exception {
        List<Class<?>> classes = new ArrayList<>();
        String path = packageName.replace('.', '/');

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(path);

        if (resource == null) {
            throw new IllegalArgumentException("Package introuvable : " + packageName);
        }

        File directory = new File(resource.getFile());
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    // verifier si il s'agit d'un sous-dossier (sous-package) en premier,
                    // pour ne pas tenter de le charger comme une classe
                    if (file.isDirectory()) {
                        List<Class<?>> sousClasses = getClassesInPackage(packageName + "." + file.getName());
                        classes.addAll(sousClasses);
                    } else if (file.getName().endsWith(".class") && !file.getName().contains("$")) {
                        String className = packageName + "." + file.getName().substring(0, file.getName().length() - 6);
                        classes.add(Class.forName(className));
                    }
                }
            }
        }
        return classes;
    }
}
