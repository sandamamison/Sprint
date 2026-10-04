# MonFramework

Mini-framework web Java basé sur Jakarta Servlet, les annotations et la réflexion. Il permet de déclarer des contrôleurs, d'associer leurs méthodes à des URLs et de retourner soit du texte, soit une vue avec des données.

## Vue d'ensemble

Le fonctionnement général est le suivant :

```text
Démarrage de l'application
		|
		v
AppInitializer
		|
		+--> scanne le package des contrôleurs
		+--> construit urlMap
		+--> initialise la connexion PostgreSQL

Requête HTTP
		|
		v
ProcessRequest
		|
		+--> cherche un fichier .html
		+--> cherche la route dans urlMap
		+--> instancie le contrôleur
		+--> exécute sa méthode
		+--> renvoie du texte ou une vue
```

## Structure du projet

| Élément | Rôle |
|---|---|
| `src/main/java/ProcessRequest.java` | Servlet principal qui traite les requêtes GET et POST. |
| `src/main/java/listner/AppInitializer.java` | Initialise les routes et le contexte au démarrage de l'application. |
| `src/main/java/annotation/Controller.java` | Marque une classe comme contrôleur. |
| `src/main/java/annotation/GetMapping.java` | Associe une méthode à une route GET. |
| `src/main/java/annotation/PostMapping.java` | Associe une méthode à une route POST. |
| `src/main/java/annotation/UrlMapping.java` | Permet de choisir l'URL et la méthode HTTP. |
| `src/main/java/util/FinderAnnotation.java` | Recherche les contrôleurs et construit la table des routes. |
| `src/main/java/util/PackageScanner.java` | Scanner générique d'annotations, actuellement non utilisé par le démarrage. |
| `src/main/java/util/MethodExecutor.java` | Instancie un contrôleur et appelle une méthode par réflexion. |
| `src/main/java/util/ModelAndView.java` | Transporte le nom d'une vue et ses données. |
| `src/main/java/util/UrlMethod.java` | Représente une clé composée d'une URL et d'une méthode HTTP. |
| `src/main/java/util/ApplicationContext.java` | Contient les ressources partagées, actuellement la connexion PostgreSQL. |
| `script/build.sh` | Compile le projet et crée le fichier JAR. |
| `lib/servlet-api.jar` | Bibliothèque nécessaire à la compilation Servlet. |

## Déclarer un contrôleur

Une classe doit être annotée avec `@Controller` et se trouver dans le package configuré par `controleurPackage`.

```java
package com.example.controller;

import annotation.Controller;
import annotation.GetMapping;
import annotation.PostMapping;
import util.ModelAndView;

@Controller
public class HomeController {

	@GetMapping(url = "/hello")
	public String hello() {
		return "Bonjour";
	}

	@GetMapping(url = "/home")
	public ModelAndView home() {
		ModelAndView result = new ModelAndView("home");
		result.addObject("message", "Bienvenue");
		return result;
	}

	@PostMapping(url = "/save")
	public String save() {
		return "Données enregistrées";
	}
}
```

Les contrôleurs doivent actuellement respecter ces règles :

- posséder un constructeur sans argument ;
- utiliser des méthodes sans paramètres ;
- annoter chaque méthode exposée avec un mapping ;
- ne pas déclarer deux fois le même couple URL/méthode HTTP.

## Les annotations

### `@Controller`

Indique que la classe doit être détectée comme contrôleur :

```java
@Controller
public class UserController { }
```

### `@GetMapping`

Crée une route GET :

```java
@GetMapping(url = "/users")
public String users() { ... }
```

### `@PostMapping`

Crée une route POST :

```java
@PostMapping(url = "/users/create")
public String create() { ... }
```

### `@UrlMapping`

Permet de choisir explicitement la méthode HTTP :

```java
@UrlMapping(url = "/admin", method = "PUT")
public String admin() { ... }
```

La méthode par défaut est `GET`. `UrlMethod` convertit la valeur en majuscules.

## Création des routes

Au démarrage, `FinderAnnotation.getControleurMaping()` :

1. recherche les classes du package configuré et de ses sous-packages ;
2. conserve les classes annotées `@Controller` ;
3. inspecte leurs méthodes ;
4. lit `@GetMapping`, `@PostMapping` et `@UrlMapping` ;
5. crée une clé `UrlMethod(url, méthode HTTP)` ;
6. enregistre cette clé avec la méthode Java correspondante.

La table obtenue est de type :

```java
HashMap<UrlMethod, Method>
```

`GET /users` et `POST /users` peuvent donc exister en même temps. En revanche, deux définitions identiques provoquent une `IllegalArgumentException` au démarrage.

## Cycle de vie

### 1. Démarrage : `AppInitializer`

`AppInitializer` est un `ServletContextListener` détecté grâce à `@WebListener`.

Dans `contextInitialized()` :

- le paramètre `controleurPackage` est lu ;
- les routes sont enregistrées dans le contexte sous le nom `urlMap` ;
- `ApplicationContext` est créé ;
- une connexion PostgreSQL est ouverte ;
- le contexte est enregistré sous le nom `MY_FRAMEWORK_CONTEXT`.

### 2. Initialisation : `ProcessRequest.init()`

Le servlet récupère dans le `ServletContext` :

- `urlMap` ;
- `view-prefix` ;
- `view-suffix`.

### 3. Arrêt

Lors de l'arrêt de l'application, `ApplicationContext.close()` ferme la connexion PostgreSQL.

## Traitement d'une requête

`doGet()` et `doPost()` appellent tous les deux `processRequest()`.

### Fichiers HTML

Si le dernier segment de l'URL se termine par `.html`, le servlet cherche ce fichier à la racine de l'application web. S'il existe, son contenu est copié dans la réponse. Sinon, une erreur 404 est envoyée.

### Routes dynamiques

Pour les autres URLs, le servlet crée une clé à partir de la méthode HTTP et du dernier segment de l'URI, puis cherche cette clé dans `urlMap`.

Exemple :

```text
/hello       -> /hello
/users/list  -> /list
```

Le deuxième exemple est important : le code actuel ne conserve que le dernier segment et ne recherche pas l'URI complète.

### Exécution

`MethodExecutor` :

1. récupère la classe qui contient la méthode ;
2. crée une nouvelle instance avec le constructeur sans argument ;
3. invoque la méthode sans paramètre.

Les contrôleurs sont donc recréés à chaque requête.

## Les réponses

### Résultat texte

Si la méthode ne retourne pas un `ModelAndView`, le résultat est écrit en texte dans la réponse.

```java
@GetMapping(url = "/status")
public String status() {
	return "OK";
}
```

### `ModelAndView`

`ModelAndView` contient :

- le nom logique de la vue ;
- une map d'attributs à transmettre à la vue.

```java
@GetMapping(url = "/profile")
public ModelAndView profile() {
	ModelAndView result = new ModelAndView("profile");
	result.addObject("username", "Naina");
	return result;
}
```

Le chemin de la vue est construit ainsi :

```text
view-prefix + nom-de-la-vue + view-suffix
```

Avec :

```text
view-prefix = /WEB-INF/views/
view-suffix = .jsp
nom de vue = profile
```

le framework effectue un `forward()` vers :

```text
/WEB-INF/views/profile.jsp
```

Avant le transfert, les valeurs du modèle sont placées dans la requête avec `req.setAttribute()`.

## Configuration attendue

Le dépôt ne contient pas de `web.xml`, mais le framework attend notamment ces paramètres :

```xml
<context-param>
	<param-name>controleurPackage</param-name>
	<param-value>com.example.controller</param-value>
</context-param>

<context-param>
	<param-name>view-prefix</param-name>
	<param-value>/WEB-INF/views/</param-value>
</context-param>

<context-param>
	<param-name>view-suffix</param-name>
	<param-value>.jsp</param-value>
</context-param>

<servlet>
	<servlet-name>ProcessRequest</servlet-name>
	<servlet-class>ProcessRequest</servlet-class>
</servlet>

<servlet-mapping>
	<servlet-name>ProcessRequest</servlet-name>
	<url-pattern>/*</url-pattern>
</servlet-mapping>
```

`AppInitializer` est découvert automatiquement si le conteneur Servlet prend en charge `@WebListener`.

## Base de données

`ApplicationContext.initialize()` tente de charger PostgreSQL et d'ouvrir la connexion suivante :

```text
URL      : jdbc:postgresql://localhost:5432/ma_base
Utilisateur : postgres
Mot de passe : password
```

La connexion est accessible avec :

```java
Connection connection = (Connection) context.getBean("databaseConnection");
```

Pour que cette partie fonctionne :

- PostgreSQL doit être installé et démarré ;
- la base `ma_base` doit exister ;
- les identifiants doivent correspondre au code ;
- le pilote JDBC PostgreSQL doit être disponible au runtime.

Les identifiants sont actuellement écrits directement dans `ApplicationContext.java`, ce qui doit être amélioré pour un usage réel.

## Compilation

Depuis le dossier `FrameWork` :

```bash
bash script/build.sh
```

Le script :

1. supprime `bin/` et `dist/` ;
2. compile tous les fichiers Java ;
3. place les classes compilées dans `bin/` ;
4. crée `dist/ProcessRequest.jar`.

Les dossiers `FrameWork/bin/` et `FrameWork/dist/` sont ignorés par Git car ils sont générés par le build.

## `PackageScanner`

`PackageScanner` fournit une autre méthode de recherche d'annotations avec :

```java
scanPackage(String packageName, String annotation)
```

Cette classe n'est pas utilisée par `AppInitializer`. Le mécanisme actif est `FinderAnnotation`.

## Limites actuelles

- Les méthodes de contrôleur ne reçoivent pas encore `HttpServletRequest` ou `HttpServletResponse`.
- Les paramètres de formulaire et les paramètres d'URL ne sont pas injectés.
- Les routes dynamiques comme `/users/{id}` ne sont pas prises en charge.
- La résolution utilise le dernier segment de l'URI au lieu de l'URI complète.
- Seuls GET et POST sont directement gérés par `ProcessRequest`.
- Une route inconnue produit une réponse texte sans envoyer explicitement le statut HTTP 404.
- Les contrôleurs doivent avoir un constructeur sans argument.
- Les contrôleurs sont recréés à chaque requête.
- Les erreurs sont renvoyées sous forme de texte au client.
- Les paramètres PostgreSQL sont codés en dur.
- Le scan des classes est principalement prévu pour des fichiers `.class` accessibles sur le système de fichiers.
- Le dossier `listner` contient une faute d'orthographe, mais ce nom fait partie du package actuel.

## Ordre conseillé pour lire le code

1. `UrlMethod.java` : comprendre la clé de routage.
2. Les fichiers du dossier `annotation` : comprendre les annotations.
3. `FinderAnnotation.java` : comprendre la découverte des routes.
4. `AppInitializer.java` : comprendre le démarrage.
5. `ProcessRequest.java` : suivre une requête.
6. `MethodExecutor.java` : comprendre la réflexion.
7. `ModelAndView.java` : comprendre le rendu des vues.
8. `ApplicationContext.java` : comprendre la connexion PostgreSQL.
9. `script/build.sh` : comprendre la compilation.

## Résumé

Ce projet scanne des classes `@Controller` au démarrage, transforme leurs annotations en table `(URL, méthode HTTP)`, puis invoque par réflexion la méthode correspondante lorsqu'une requête HTTP arrive.
