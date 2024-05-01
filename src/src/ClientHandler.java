import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class ClientHandler extends Thread {

    private boolean debugMode = true;
    private enum transferType {
        ASCII, BINARY
    }
    private enum userStatus {
        NOTLOGGEDIN, ENTEREDUSERNAME, LOGGEDIN
    }

    // Path information
    private String root;
    private String currDirectory;
    private String fileSeparator = "/";

    // control connection
    private Socket controlSocket;
    private PrintWriter controlOutWriter;
    private BufferedReader controlIn;

    // data Connection
    private ServerSocket dataSocket;
    private Socket dataConnection;
    private PrintWriter dataOutWriter;

    private int dataPort;
    private transferType transferMode = transferType.ASCII;

    // user properly logged in?
    private userStatus currentUserStatus = userStatus.NOTLOGGEDIN;
    private String validUser = "server";
    private String validPassword = "server";

    private boolean quitCommandLoop = false;

    public ClientHandler(Socket client, int port) {
        super();
        this.controlSocket = client;
        this.dataPort = port;
        this.currDirectory = "./files";
//        this.root = System.getProperty("user.dir");
        this.root = "./files";
    }

    public void run() {
        debugOutput("Current working directory " + this.currDirectory);
        try {
            // Input from client
            controlIn = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));

            // Output to client, automatically flushed after each print
            controlOutWriter = new PrintWriter(controlSocket.getOutputStream(), true);

            // Greeting
            sendMsgToClient("220 Boas vindas ao Servidor FTP IFRS");

            // Get new command from client
            while (!quitCommandLoop) {
                executeCommand(controlIn.readLine());
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Clean up
            try {
                controlIn.close();
                controlOutWriter.close();
                controlSocket.close();
                debugOutput("Sockets encerrados e trabalho parado");
            } catch (IOException e) {
                e.printStackTrace();
                debugOutput("Não foi possível fechar sockets abertos");
            }
        }

    }

    /**
     * Main command dispatcher method. Separates the command from the arguments and
     * dispatches it to single handler functions.
     *
     * @param c the raw input from the socket consisting of command and arguments
     */

    private void executeCommand(String c) {

        if (c == null)
            return;

        // split command and arguments
        int index = c.indexOf(' ');
        String command = ((index == -1) ? c.toUpperCase() : (c.substring(0, index)).toUpperCase());
        String args = ((index == -1) ? null : c.substring(index + 1));

        debugOutput("Command: " + command + " Args: " + args);

        // dispatcher mechanism for different commands
        switch (command) {
            case "USER":
                handleUser(args);
                break;

            case "PASS":
                handlePass(args);
                break;

            case "CWD":
                handleCwd(args);
                break;

            case "LIST":
                handleNlist(args);
                break;

            case "NLST":
                handleNlst(args);
                break;

            case "PWD":
            case "XPWD":
                handlePwd();
                break;

            case "QUIT":
                handleQuit();
                break;

            case "PASV":
                handlePasv();
                break;

            case "EPSV":
                handleEpsv();
                break;

            case "SYST":
                handleSyst();
                break;

            case "FEAT":
                handleFeat();
                break;

            case "PORT":
                handlePort(args);
                break;

            case "EPRT":
                handleEPort(args);
                break;

            case "RETR":
                handleRetr(args);
                break;

            case "MKD":
            case "XMKD":
                handleMkd(args);
                break;

            case "RMD":
            case "XRMD":
                handleRmd(args);
                break;

            case "TYPE":
                handleType(args);
                break;

            case "STOR":
                handleStor(args);
                break;

            default:
                sendMsgToClient("501 Comando desconhecido");
                break;

        }

    }

    /**
     * Sends a message to the connected client over the control connection. Flushing
     * is automatically performed by the stream.
     *
     * @param msg The message that will be sent
     */
    private void sendMsgToClient(String msg) {
        controlOutWriter.println(msg);
    }

    /**
     * Send a message to the connected client over the data connection.
     *
     * @param msg Message to be sent
     */
    private void sendDataMsgToClient(String msg) {
        if (dataConnection == null || dataConnection.isClosed()) {
            sendMsgToClient("425 Conexão de dados não estabelecida");
            debugOutput("Não é possível notificar pois não há conexão de dados");
        } else {
            dataOutWriter.print(msg + '\r' + '\n');
        }

    }

    /**
     * Open a new data connection socket and wait for new incoming connection from
     * client. Used for passive mode.
     *
     * @param port Port on which to listen for new incoming connection
     */
    private void openDataConnectionPassive(int port) {

        try {
            dataSocket = new ServerSocket(port);
            dataConnection = dataSocket.accept();
            dataOutWriter = new PrintWriter(dataConnection.getOutputStream(), true);
            debugOutput("Conexão de dados - Modo Passivo - estabelecido");

        } catch (IOException e) {
            debugOutput("Não foi possível criar conexão de dados.");
            e.printStackTrace();
        }

    }

    /**
     * Connect to client socket for data connection. Used for active mode.
     *
     * @param ipAddress Client IP address to connect to
     * @param port      Client port to connect to
     */
    private void openDataConnectionActive(String ipAddress, int port) {
        try {

            if(port == controlSocket.getPort()) {
                dataConnection = controlSocket;
                dataOutWriter = new PrintWriter(dataConnection.getOutputStream(), true);
                return;
            }

            dataConnection = new Socket(ipAddress, port);
            dataOutWriter = new PrintWriter(dataConnection.getOutputStream(), true);
            debugOutput("Data connection - Active Mode - established");
        } catch (IOException e) {
            debugOutput("Could not connect to client data socket: "+e.getMessage());
            e.printStackTrace();
        }

    }

    /**
     * Close previously established data connection sockets and streams
     */
    private void closeDataConnection() {
        try {
            dataOutWriter.close();
            dataConnection.close();
            if (dataSocket != null) {
                dataSocket.close();
            }

            debugOutput("Data connection was closed");
        } catch (IOException e) {
            debugOutput("Could not close data connection");
            e.printStackTrace();
        }
        dataOutWriter = null;
        dataConnection = null;
        dataSocket = null;
        quitCommandLoop = true;
    }

    /**
     * Handler for USER command. User identifies the client.
     *
     * @param username Username entered by the user
     */
    private void handleUser(String username) {
        if (username.toLowerCase().equals(validUser)) {
            sendMsgToClient("331 User name okay, need password");
            currentUserStatus = userStatus.ENTEREDUSERNAME;
        } else if (currentUserStatus == userStatus.LOGGEDIN) {
            sendMsgToClient("530 User already logged in");
        } else {
            sendMsgToClient("530 Not logged in");
        }
    }

    /**
     * Handler for PASS command. PASS receives the user password and checks if it's
     * valid.
     *
     * @param password Password entered by the user
     */

    private void handlePass(String password) {
        // User has entered a valid username and password is correct
        if (currentUserStatus == userStatus.ENTEREDUSERNAME && password.equals(validPassword)) {
            currentUserStatus = userStatus.LOGGEDIN;
            sendMsgToClient("230 User logged in successfully");
        }

        // User is already logged in
        else if (currentUserStatus == userStatus.LOGGEDIN) {
            sendMsgToClient("530 User already logged in");
        }

        // Wrong password
        else {
            sendMsgToClient("530 Not logged in");
        }
    }

    /**
     * Handler for CWD (change working directory) command.
     *
     * @param args New directory to be created
     */
    private void handleCwd(String args) {
        String filename = currDirectory;

        // go one level up (cd ..)
        if (args.equals("..")) {
            int ind = filename.lastIndexOf(fileSeparator);
            if (ind > 0) {
                filename = filename.substring(0, ind);
            }
        }

        // if argument is anything else (cd . does nothing)
        else if ((args != null) && (!args.equals("."))) {
            filename = filename + fileSeparator + args;
        }

        // check if file exists, is directory and is not above root directory
        File f = new File(filename);

        if (f.exists() && f.isDirectory() && (filename.length() >= root.length())) {
            currDirectory = filename;
            System.out.println("Directory changed to "+currDirectory);
            sendMsgToClient("250 " + currDirectory);
        } else {
            sendMsgToClient("550 Requested action not taken. File unavailable.");
        }
    }

    /**
     * Handler for NLST (Named List) command. Lists the directory content in a short
     * format (names only)
     *
     * @param args The directory to be listed
     */


    private void handleNlist(String args) {

        String[] fileAndPath = args.split(" ");

        if(fileAndPath.length >= 1 && !fileAndPath[0].equals("")) {
            currDirectory += fileSeparator + fileAndPath[0];
        }

        File folder = new File(currDirectory);
        File[] files = folder.listFiles();
        String filesStr = "";
        StringBuilder filesStringBuilder = new StringBuilder(filesStr);

        sendMsgToClient("125 Opening ASCII mode data connection for file list.");

        for (int x = 0; x < files.length; x++) {
            if (files[x].isFile()){
                filesStringBuilder.append(files[x].getName());

                if(x < (files.length - 1))
                    filesStringBuilder.append("&");
            }
        }

        if (files.length > 0)
            sendMsgToClient("226 "+filesStringBuilder.toString());
        else
            sendMsgToClient("500 não há arquivos no servidor");
    }

    private void handleNlst(String args) {
        if (dataConnection == null || dataConnection.isClosed()) {
            sendMsgToClient("425 Sem conexão estabelecida");
        } else {

                String[] dirContent = nlstHelper(args);

                if (dirContent == null) {
                    sendMsgToClient("550 Arquivo não existe");
                } else {
                    sendMsgToClient("125 Abrindo modo ASCII para conexão e transmissão de arquivos.");

                    for (int i = 0; i < dirContent.length; i++) {
                        sendDataMsgToClient(dirContent[i]);
                    }

                    sendMsgToClient("226 Transferência completa.");
                    closeDataConnection();

            }

        }

    }

    /**
     * A helper for the NLST command. The directory name is obtained by appending
     * "args" to the Diretório atual: 
     *
     * @param args The directory to list
     * @return an array containing names of files in a directory. If the given name
     *         is that of a file, then return an array containing only one element
     *         (this name). If the file or directory does not exist, return nul.
     */
    private String[] nlstHelper(String args) {
        String filename = currDirectory;
        if (args != null) {
            filename = filename + fileSeparator + args;
        }

        File f = new File(filename);

        if (f.exists() && f.isDirectory()) {
            return f.list();
        } else if (f.exists() && f.isFile()) {
            String[] allFiles = new String[1];
            allFiles[0] = f.getName();
            return allFiles;
        } else {
            return null;
        }
    }

    /**
     * Handler for the PORT command. The client issues a PORT command to the server
     * in active mode, so the server can open a data connection to the client
     * through the given address and port number.
     *
     * @param args The first four segments (separated by comma) are the IP address.
     *             The last two segments encode the port number (port = seg1*256 +
     *             seg2)
     */
    private void handlePort(String args) {

        String[] stringSplit = args.split(",");
        String hostName = stringSplit[0] + "." + stringSplit[1] + "." + stringSplit[2] + "." + stringSplit[3];

        int p = Integer.parseInt(stringSplit[4]) * 256 + Integer.parseInt(stringSplit[5]);

        openDataConnectionActive(hostName, p);
        sendMsgToClient("200 OK");
    }

    /**
     * Handler for the EPORT command. The client issues an EPORT command to the
     * server in active mode, so the server can open a data connection to the client
     * through the given address and port number.
     *
     * @param args This string is separated by vertical bars and encodes the IP
     *             version, the IP address and the port number
     */
    private void handleEPort(String args) {
        final String IPV4 = "1";
        final String IPV6 = "2";

        // Example arg: |2|::1|58770| or |1|132.235.1.2|6275|
        String[] splitArgs = args.split("\\|");
        String ipVersion = splitArgs[1];
        String ipAddress = splitArgs[2];

        if (!IPV4.equals(ipVersion) || !IPV6.equals(ipVersion)) {
            throw new IllegalArgumentException("Versão IP não suportada");
        }
    }

    /**
     * Handler for PWD (Print working directory) command. Returns the path of the
     * Diretório atual:  back to the client.
     */

    private void handlePwd() {
        System.out.println("Diretório atual:  "+currDirectory);
        sendMsgToClient("257 " + currDirectory);
    }

    /**
     * Handler for PASV command which initiates the passive mode. In passive mode
     * the client initiates the data connection to the server. In active mode the
     * server initiates the data connection to the client.
     */
    private void handlePasv() {
        // Using fixed IP for connections on the same machine
        // For usage on separate hosts, we'd need to get the local IP address from
        // somewhere
        // Java sockets did not offer a good method for this
        String myIp = "127.0.0.1";
        String myIpSplit[] = myIp.split("\\.");

        int p1 = dataPort / 256;
        int p2 = dataPort % 256;

        sendMsgToClient("227 Entrando em modo passivo (" + myIpSplit[0] + "," + myIpSplit[1] + "," + myIpSplit[2] + ","
                + myIpSplit[3] + "," + p1 + "," + p2 + ")");

        openDataConnectionPassive(dataPort);

    }

    /**
     * Handler for EPSV command which initiates extended passive mode. Similar to
     * PASV but for newer clients (IPv6 support is possible but not implemented
     * here).
     */
    private void handleEpsv() {
        sendMsgToClient("229 Entrando em modo passivo extendido (|||" + dataPort + "|)");
        openDataConnectionPassive(dataPort);
    }

    /**
     * Handler for the QUIT command.
     */
    private void handleQuit() {
        sendMsgToClient("221 Fechando conexão");
        quitCommandLoop = true;
    }

    private void handleSyst() {
        sendMsgToClient("215 IFRS SERVIDOR FTP");
    }

    /**
     * Handler for the FEAT (features) command. Feat transmits the
     * abilities/features of the server to the client. Needed for some ftp clients.
     * This is just a dummy message to satisfy clients, no real feature information
     * included.
     */
    private void handleFeat() {
        sendMsgToClient("211-Extensões suportadas:");
        sendMsgToClient("211 Fim");
    }

    /**
     * Handler for the MKD (make directory) command. Creates a new directory on the
     * server.
     *
     * @param args Directory name
     */
    private void handleMkd(String args) {
        // Allow only alphanumeric characters
        if (args != null && args.matches("^[a-zA-Z0-9]+$")) {
            File dir = new File(currDirectory + fileSeparator + args);

            if (!dir.mkdir()) {
                sendMsgToClient("550 Falha ao criar novo diretório");
                debugOutput("Falha ao criar novo diretório");
            } else {
                sendMsgToClient("250 Diretório criado com sucesso");
            }
        } else {
            sendMsgToClient("550 nome inválido");
        }

    }

    /**
     * Handler for RMD (remove directory) command. Removes a directory.
     *
     * @param dir directory to be deleted.
     */
    private void handleRmd(String dir) {
        String filename = currDirectory;

        // only alphanumeric folder names are allowed
        if (dir != null && dir.matches("^[a-zA-Z0-9]+$")) {
            filename = filename + fileSeparator + dir;

            // check if file exists, is directory
            File d = new File(filename);

            if (d.exists() && d.isDirectory()) {
                d.delete();

                sendMsgToClient("250 Diretório removido com sucesso");
            } else {
                sendMsgToClient("550 Ação não realizada. Arquivo inexistente.");
            }
        } else {
            sendMsgToClient("550 Nome inválido de arquivo.");
        }

    }

    /**
     * Handler for the TYPE command. The type command sets the transfer mode to
     * either binary or ascii mode
     *
     * @param mode Transfer mode: "a" for Ascii. "i" for image/binary.
     */
    private void handleType(String mode) {
        if (mode.toUpperCase().equals("A")) {
            transferMode = transferType.ASCII;
            sendMsgToClient("200 OK");
        } else if (mode.toUpperCase().equals("I")) {
            transferMode = transferType.BINARY;
            sendMsgToClient("200 OK");
        } else
            sendMsgToClient("504 NOK");
        ;

    }

    /**
     * Handler for the RETR (retrieve) command. Retrieve transfers a file from the
     * ftp server to the client.
     *
     * @param file The file to transfer to the user
     */
    private void handleRetr(String file) {

        String[] fileAndPath = file.split(" ");

        if(fileAndPath.length > 1 && !fileAndPath[1].equals("")) {
            currDirectory += fileSeparator + fileAndPath[1];
        }

        File f = new File(currDirectory + fileSeparator + fileAndPath[0]);

        if (!f.exists()) {
            sendMsgToClient("550 Arquivo não existe");
        }

        else {

            // Binary mode
            if (transferMode == transferType.BINARY) {
                BufferedOutputStream fout = null;
                BufferedInputStream fin = null;

                sendMsgToClient("150 " + f.getName());

                try {
                    // create streams
                    fout = new BufferedOutputStream(dataConnection.getOutputStream());
                    fin = new BufferedInputStream(new FileInputStream(f));
                } catch (Exception e) {
                    debugOutput("Não foi possível criar stream de arquivos");
                }

                debugOutput("Iniciando transmissão do arquivo " + f.getName());

                // write file with buffer
                byte[] buf = new byte[1024];
                int l = 0;
                try {
                    while ((l = fin.read(buf, 0, 1024)) != -1) {
                        fout.write(buf, 0, l);
                    }
                } catch (IOException e) {
                    debugOutput("Não foi possível ler ou escrever no stream de arquivo");
                    e.printStackTrace();
                }

                // close streams
                try {
                    fin.close();
                    fout.close();
                } catch (IOException e) {
                    debugOutput("Não foi possível fechar o stream de arquivo");
                    e.printStackTrace();
                }

                debugOutput("Transmissão de arquivo completa " + f.getName());

                sendMsgToClient("226 Transferência de arquivo realizada com sucesso. Fechando conexão de dados.");

            }

            // ASCII mode
            else {
                sendMsgToClient("150 Abrindo modo ASCII de conexao de dados para arquivo requisitado " + f.getName());
                System.out.println("150 Abrindo modo ASCII de conexao de dados para arquivo requisitado " + f.getName());

                BufferedReader rin = null;
                PrintWriter rout = null;

                try {
                    rin = new BufferedReader(new FileReader(f));
                    rout = new PrintWriter(dataConnection.getOutputStream(), true);

                } catch (IOException e) {
                    debugOutput("Não foi possível criar stream de arquivos");
                }

                String s;

                try {
                    while ((s = rin.readLine()) != null) {
                        rout.println(s);
                    }
                } catch (IOException e) {
                    debugOutput("Não foi possível ler ou escrever no stream de arquivo");
                    e.printStackTrace();
                }

                try {
                    rout.close();
                    rin.close();
                } catch (IOException e) {
                    debugOutput("Não foi possível fechar o stream de arquivo");
                    e.printStackTrace();
                }
                sendMsgToClient("226 Transferência de arquivo realizada com sucesso. Fechando conexão de dados.");
            }

        }
        closeDataConnection();

    }

    /**
     * Handler for STOR (Store) command. Store receives a file from the client and
     * saves it to the ftp server.
     *
     * @param file The file that the user wants to store on the server
     */
    private void handleStor(String file) {
        if (file == null) {
            sendMsgToClient("501 Sem nome de arquivo informado");
        } else {

            String[] fileAndPath = file.split(" ");

            if(fileAndPath.length > 1 && !fileAndPath[1].equals("")) {
                currDirectory += fileSeparator + fileAndPath[1];
            }

            File f = new File(currDirectory + fileSeparator + fileAndPath[0]);

            if (f.exists()) {
                sendMsgToClient("550 Arquivo já existe");
            }

            else {

                // Binary mode
                if (transferMode == transferType.BINARY) {
                    BufferedOutputStream fout = null;
                    BufferedInputStream fin = null;

                    sendMsgToClient("150 Abrindo modo binário para conexão e transimissão de aquivo requisitado " + f.getName());

                    try {
                        // create streams
                        fout = new BufferedOutputStream(new FileOutputStream(f));
                        fin = new BufferedInputStream(dataConnection.getInputStream());
                    } catch (Exception e) {
                        debugOutput("Não foi possível criar stream de arquivos");
                    }

                    debugOutput("Recepção de arquivo iniciada " + f.getName());

                    // write file with buffer
                    byte[] buf = new byte[1024];
                    int l = 0;
                    try {
                        while ((l = fin.read(buf, 0, 1024)) != -1) {
                            fout.write(buf, 0, l);
                        }
                    } catch (IOException e) {
                        debugOutput("Não foi possível ler ou escrever no stream de arquivo");
                        e.printStackTrace();
                    }

                    // close streams
                    try {
                        fin.close();
                        fout.close();
                    } catch (IOException e) {
                        debugOutput("Não foi possível fechar o stream de arquivo");
                        e.printStackTrace();
                    }

                    debugOutput("Completed receiving file " + f.getName());

                    sendMsgToClient("226 Transferência de arquivo realizada com sucesso. Fechando conexão de dados.");

                }

                // ASCII mode
                else {
                    System.out.println("iniciando copia de arquivo");
                    sendMsgToClient("150 Abrindo modo ASCII de conexao de dados para arquivo requisitado " + f.getName());

                    BufferedReader rin = null;
                    PrintWriter rout = null;

                    try {
                        rin = new BufferedReader(new InputStreamReader(dataConnection.getInputStream()));
                        rout = new PrintWriter(new FileOutputStream(f), true);

                    } catch (IOException e) {
                        debugOutput("Não foi possível criar stream de arquivos");
                    }

                    String s;

                    try {
                        while ((s = rin.readLine()) != null) {
                            rout.println(s);
                        }
                    } catch (IOException e) {
                        debugOutput("Não foi possível ler ou escrever no stream de arquivo");
                        e.printStackTrace();
                    }

                    try {
                        rout.close();
                        rin.close();
                    } catch (IOException e) {
                        debugOutput("Não foi possível fechar o stream de arquivo");
                        e.printStackTrace();
                    }

                    System.out.println("finalizando copia de arquivo");
                    sendMsgToClient("226 Transferência de arquivo realizada com sucesso. Fechando conexão de dados.");
                }

            }
        }

    }

    /**
     * Debug output to the console. Also includes the Thread ID for better
     * readability.
     *
     * @param msg Debug message
     */
    private void debugOutput(String msg) {
        if (debugMode) {
            System.out.println("Thread " + this.getId() + ": " + msg);
        }
    }

}
