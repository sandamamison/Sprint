package util;

import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.lang.annotation.Annotation;
import java.net.URL;

public class PackageScanner {
    public List<Class<?>> scanPackage(String packageName, String annotation) {
        List<Class<?>> listeClasse = new ArrayList<>();

        String path = packageName.replace(".", "/");

        try {
            Class<?> rawAnnotation = Class.forName(annotation);

            if (!rawAnnotation.isAnnotation()) {
                System.out.println(annotation + " n'est pas une annotation !");
                return listeClasse;
            }

            Class<? extends Annotation> annotationCible = (Class<? extends Annotation>) rawAnnotation;

            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            URL resource = classLoader.getResource(path);

            if (resource == null) {
                System.out.println("Y a pas de classe dans :" + path);
                return listeClasse;
            }

            String protocole = resource.getProtocol();

            if ("file".equals(protocole)) {
                File folder = new File(resource.toURI());
                File[] files = folder.listFiles();

                if (files != null) {
                    for (File file : files) {
                        if (file.getName().endsWith(".class")) {
                            String className = file.getName().substring(0, file.getName().length() - 6);
                            String fullClassName = packageName + "." + className;
                            Class<?> annot = Class.forName(fullClassName);

                            if (annot.isAnnotationPresent(annotationCible)) {
                                listeClasse.add(annot);
                            }
                        }
                    }
                }
            }
            return listeClasse;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return listeClasse;
    }
}