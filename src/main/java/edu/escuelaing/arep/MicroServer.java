package edu.escuelaing.arep;

import edu.escuelaing.arep.annotations.GetMapping;
import edu.escuelaing.arep.annotations.RequestParam;
import edu.escuelaing.arep.annotations.RestController;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.*;
import java.util.*;

public class MicroServer {

    public static Map<String, Method> services = new HashMap<>();
    private static final int port = 8080;
    public static final String resourcesPath = "src/main/java/edu/escuelaing/arep/resources";
    private static Boolean running = true;
    private static ServerSocket serverSocket;

    public static void main(String[] args) throws Exception {
        loadComponents();
        System.out.println("Listo para recibir ...");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                stopServer();
                System.out.println("\nServidor apagado correctamente.");
            } catch (IOException e) {
                System.err.println("Error al cerrar el servidor: " + e.getMessage());
            }
        }));

        try {
            serverSocket = new ServerSocket(port);
            while (running) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new HttpServer(clientSocket)).start();
            }
        } catch (IOException e) {
            if (running) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Cierra el servidor de forma segura
     */
    public static void stopServer() throws IOException {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        System.out.println("Cerrando servidor...");
    }

    // Se pausa debido a que no van a entrar parametros por consola en la ejecucion (java -cp ...)
    // /** 
    //  * Loads the services reading the annotations from the service class
    //  * 
    //  * @param args
    //  * @throws ClassNotFoundException
    //  */
    // public static void loadComponents(String[] args) throws Exception {
    //     for (int i = 0; i < args.length; i++) {
    //         Class c = Class.forName(args[i]);
    //         String[] arguments = Arrays.copyOfRange(args, 1, args.length);

    //         if (!c.isAnnotationPresent(RestController.class)) {
    //             System.exit(0);
    //         }

    //         for (Method m : c.getDeclaredMethods()) {
    //             if (m.isAnnotationPresent(GetMapping.class)) {
    //                 // a=anotacion jaja
    //                 GetMapping a = m.getAnnotation(GetMapping.class);
    //                 services.put(a.value(), m);
    //             }
    //         }
    //     }
    // }

    /**
     * Loads the services reading the annotations from the services classes
     */
    public static void loadComponents() {
        try {
            String packageName = "edu.escuelaing.arep.services";
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            String path = packageName.replace('.', '/');
            URL resource = classLoader.getResource(path);
            if (resource == null) {
                System.out.println("No se encontraron servicios en el paquete: " + packageName);
                return;
            }
            File directory = new File(resource.getFile());
            if (!directory.exists()) {
                System.out.println("Directorio de servicios no encontrado.");
                return;
            }
            for (String fileName : directory.list()) {
                if (fileName.endsWith(".class")) {
                    String className = packageName + "." + fileName.replace(".class", "");
                    Class<?> serviceClass = Class.forName(className);
                    if (serviceClass.isAnnotationPresent(RestController.class)) {
                        for (Method m : serviceClass.getDeclaredMethods()) {
                            if (m.isAnnotationPresent(GetMapping.class)) {
                                GetMapping a = m.getAnnotation(GetMapping.class);
                                services.put(a.value(), m);
                                System.out.println("Cargué el servicio: " + a.value());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    

    public static String simulateRequests(String route) throws Exception {
        Method m = services.get(route);
        String response = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json\r\n"
                + "\r\n"
                + "{\"name\": " + "\"" + m.invoke(null, "pedro") + "\"}";
        return response;
    }
}

/**
 * Starts the web server and handles the client requests
 */
class HttpServer implements Runnable{

    private static final String staticFilePath = "src/main/java/edu/escuelaing/arep/resources";

    private Socket clientSocket;

    public HttpServer(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
             BufferedOutputStream dataOut = new BufferedOutputStream(clientSocket.getOutputStream())) {

            String requestLine = in.readLine();
            if (requestLine == null) return;

            String[] tokens = requestLine.split(" ");
            String method = tokens[0];
            String fileRequested = tokens[1];
            if (fileRequested.startsWith("/app")) {
                handleAppRequest(method, fileRequested, out);
            } else {
                handleFileRequest(method, fileRequested, out, dataOut);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void handleFileRequest(String method, String fileRequested, PrintWriter out, BufferedOutputStream dataOut) throws IOException {
        if (method.equals("GET")) {
            if (fileRequested.equals("/")) {
                fileRequested = "/index.html";
            }

            File file = new File(staticFilePath + fileRequested);
            if (!file.exists() || file.isDirectory()) {
                out.println("HTTP/1.1 404 Not Found");
                out.println("Content-Type: text/html");
                out.println();
                out.println("<html><body><h1>404 - Archivo no encontrado</h1></body></html>");
                out.flush();
                return;
            }

            int fileLength = (int) file.length();
            String contentType = getContentType(fileRequested);
            byte[] fileData = readFileData(file, fileLength);

            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: " + contentType);
            out.println("Content-Length: " + fileLength);
            out.println();
            out.flush();

            dataOut.write(fileData, 0, fileLength);
            dataOut.flush();
        }
    }

    private static void handleAppRequest(String method, String fileRequested, PrintWriter out) {
        out.println("HTTP/1.1 200 OK");
        out.println("Content-type: application/json");
        out.println();

        String[] pathAndQuery = fileRequested.substring(fileRequested.indexOf("/app/") + 4).split("\\?");
        String path = pathAndQuery[0];
        String query = pathAndQuery.length > 1 ? pathAndQuery[1] : "";

        Method serviceMethod = MicroServer.services.get(path);
        if (serviceMethod != null) {
            try {
                Map<String, String> queryParams = parseQueryParams(query);
                Object[] parameters = getServiceMethodParameters(serviceMethod, queryParams);
                String response = (String) serviceMethod.invoke(null, parameters);
                out.println(response);
            } catch (Exception e) {
                e.printStackTrace();
                out.println("Error ejecutando el servicio");
            }
        } else {
            out.println("Servicio no encontrado");
        }

        out.flush();
    }

    private static Object[] getServiceMethodParameters(Method serviceMethod, Map<String, String> queryParams) {
        Object[] parameters = new Object[serviceMethod.getParameterCount()];
        Class<?>[] parameterTypes = serviceMethod.getParameterTypes();
        Annotation[][] annotations = serviceMethod.getParameterAnnotations();

        for (int i = 0; i < annotations.length; i++) {
            for (Annotation annotation : annotations[i]) {
                if (annotation instanceof RequestParam) {
                    RequestParam requestParam = (RequestParam) annotation;
                    String paramName = requestParam.value();
                    String paramValue = queryParams.get(paramName);

                    if (paramValue == null || paramValue.isEmpty()) {
                        parameters[i] = convertToType(parameterTypes[i], requestParam.defaultValue());
                    } else {
                        parameters[i] = convertToType(parameterTypes[i], paramValue);
                    }
                }
            }
        }
        return parameters;
    }

    private static Object convertToType(Class<?> type, String value) {
        if (type == int.class || type == Integer.class) return Integer.parseInt(value);
        if (type == double.class || type == Double.class) return Double.parseDouble(value);
        if (type == float.class || type == Float.class) return Float.parseFloat(value);
        if (type == long.class || type == Long.class) return Long.parseLong(value);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(value);
        return value; 
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> queryParams = new HashMap<>();
        if (query != null && !query.isEmpty()) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    queryParams.put(keyValue[0], keyValue[1]);
                }
            }
        }
        return queryParams;
    }

    private static String getContentType(String filePath) {
        if (filePath.endsWith(".html")) return "text/html";
        if (filePath.endsWith(".css")) return "text/css";
        if (filePath.endsWith(".js")) return "application/javascript";
        if (filePath.endsWith(".png")) return "image/png";
        if (filePath.endsWith(".jpg") || filePath.endsWith(".jpeg")) return "image/jpeg";
        return "text/plain";
    }

    private static byte[] readFileData(File file, int fileLength) throws IOException {
        try (FileInputStream fileIn = new FileInputStream(file)) {
            byte[] fileData = new byte[fileLength];
            fileIn.read(fileData);
            return fileData;
        }
    }
}