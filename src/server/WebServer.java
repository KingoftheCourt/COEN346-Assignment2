package server;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

//create the WebServer class to receive connections on port 5000. Each connection is handled by a master thread that puts the descriptor in a bounded buffer. A pool of worker threads take jobs from this buffer if there are any to handle the connection.
public class WebServer {

    private static Semaphore Semaphore = new Semaphore(1);
    private final ExecutorService threadPool;
    private static final int threadPoolSize=10;

    public WebServer()
    {
        this.threadPool=Executors.newFixedThreadPool(threadPoolSize);

    }

    public void start() throws java.io.IOException{
        //Create a server socket
        ServerSocket serverSocket = new ServerSocket(5000);
        while(true){
            System.out.println("Waiting for a client to connect...");
            //Accept a connection from a client
            Socket clientSocket = serverSocket.accept();
            System.out.println("New client...");

            threadPool.execute(() -> {
                try{
                    handleClientRequest(clientSocket);

                }

                catch(IOException e)
                {
                    e.printStackTrace();
                }

            });

        }
    }


    private void handleClientRequest(Socket clientSocket) throws IOException
    {
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));   //object that has capability to read input from client
        OutputStream out = clientSocket.getOutputStream();  //object that has capability to give output to client

        String request = in.readLine();
        if (request != null) {
            if (request.startsWith("GET")) {
                // Handle GET request
                handleGetRequest(out);
            } else if (request.startsWith("POST")) {
                // Handle POST request
                handlePostRequest(in, out);
            }
        }

        in.close();
        out.close();
        clientSocket.close();

    }
    private static void handleGetRequest(OutputStream out) throws IOException {
        // Respond with a basic HTML page
        System.out.println("Handling GET request");
        String response = "HTTP/1.1 200 OK\r\n\r\n" +
                "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "<title>Concordia Transfers</title>\n" +
                "</head>\n" +
                "<body>\n" +
                "\n" +
                "<h1>Welcome to Concordia Transfers</h1>\n" +
                "<p>Select the account and amount to transfer</p>\n" +
                "\n" +
                "<form action=\"/submit\" method=\"post\">\n" +
                "        <label for=\"account\">Account:</label>\n" +
                "        <input type=\"text\" id=\"account\" name=\"account\"><br><br>\n" +
                "\n" +
                "       <label for=\"senderBalance\">Sender Balance:</label>\n" +
                "        <input type=\"text\" id=\"senderBalance\" name=\"senderBalance\"><br><br>\n" +
                "\n" +
                "        <label for=\"value\">Value:</label>\n" +
                "        <input type=\"text\" id=\"value\" name=\"value\"><br><br>\n" +
                "\n" +
                "        <label for=\"toAccount\">To Account:</label>\n" +
                "        <input type=\"text\" id=\"toAccount\" name=\"toAccount\"><br><br>\n" +
                "\n" +
                "       <label for=\"receiverBalance\">Receiver Balance:</label>\n" +
                "        <input type=\"text\" id=\"receiverBalance\" name=\"receiverBalance\"><br><br>\n" +
                "\n" +
                "        <label for=\"toValue\">To Value:</label>\n" +
                "        <input type=\"text\" id=\"toValue\" name=\"toValue\"><br><br>\n" +
                "\n" +
                "        <input type=\"submit\" value=\"Submit\">\n" +
                "    </form>\n" +
                "</body>\n" +
                "</html>\n";
        out.write(response.getBytes());
        out.flush();
    }

    private static void handlePostRequest(BufferedReader in, OutputStream out) throws IOException {
        //Account account=new Account();
        System.out.println("Handling post request");
        StringBuilder requestBody = new StringBuilder();
        int contentLength = 0;
        String line;

        // Read headers to get content length
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            if (line.startsWith("Content-Length")) {
                contentLength = Integer.parseInt(line.substring(line.indexOf(' ') + 1));
            }
        }

        // Read the request body based on content length
        for (int i = 0; i < contentLength; i++) {
            requestBody.append((char) in.read());
        }

        System.out.println(requestBody.toString());
        // Parse the request body as URL-encoded parameters
        String[] params = requestBody.toString().split("&");
        String account = null, value = null, toAccount = null, toValue = null, senderBalance = null, receiverBalance = null;

        for (String param : params) {
            String[] parts = param.split("=");
            if (parts.length == 2) {
                String key = URLDecoder.decode(parts[0], "UTF-8");
                String val = URLDecoder.decode(parts[1], "UTF-8");

                switch (key) {
                    case "account":
                        account = val;
                        break;
                    case "value":
                        value = val;
                        break;
                    case "toAccount":
                        toAccount = val;
                        break;
                    case "toValue":
                        toValue = val;
                        break;
                    case "senderBalance":
                        senderBalance = val;
                        break;
                    case "receiverBalance":
                        receiverBalance = val;
                        break;
                }
            }
        }
        try {
            Semaphore.acquire();
            Account sender = new Account(Integer.parseInt(senderBalance), Integer.parseInt(account));  // storing sender info in an Account object
            Account receiver = new Account(Integer.parseInt(receiverBalance), Integer.parseInt(toAccount));   // storing receiver info in an Account object

            sender.withdraw(Integer.parseInt(value));
            receiver.deposit(Integer.parseInt(toValue));

            String tempSenderBalance = Integer.toString(sender.getBalance());
            String tempReceiverBalance = Integer.toString(receiver.getBalance());

            // Create the response
            String responseContent = "<html><body><h1>Thank you for using Concordia Transfers</h1>" +
                    "<h2>Received Form Inputs:</h2>" +
                    "<p>Account: " + account + "</p>" +
                    "<p> New Balance: " + tempSenderBalance + "</p>" +
                    "<p>To Account: " + toAccount + "</p>" +
                    "<p>New Balance: " + tempReceiverBalance + "</p>" +
                    "</body></html>";

            // Respond with the received form inputs
            String response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Length: " + responseContent.length() + "\r\n" +
                    "Content-Type: text/html\r\n\r\n" +
                    responseContent;

            out.write(response.getBytes());
            out.flush();
        } catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
        finally {
            Semaphore.release(); // Release the semaphore after accessing/modifying shared resources
        }
    }


    public static void manageTextFileTransfer(File file,int senderAccountID,int receiverAccountID,int transferAmount) {
        ExecutorService threadPool2 = Executors.newFixedThreadPool(10);
        List<Account> listAccount = new ArrayList();
        threadPool2.execute(() -> {
            try {
                Semaphore.acquire();

                try {

                    Scanner fileReader = new Scanner(file);

                    while (fileReader.hasNextLine()) {
                        String processInfo = fileReader.nextLine();
                        String[] tempProcessStorage = processInfo.split(",");
                        int AccountID = Integer.parseInt(tempProcessStorage[0]);
                        int AccountBalance = Integer.parseInt(tempProcessStorage[1]);
                        Account Account = new Account(AccountBalance, AccountID);
                        listAccount.add(Account);
                    }
                    try {
                        // Create a FileWriter with append mode set to false (to truncate the file)
                        FileWriter fw = new FileWriter(file, false);
                        fw.write(""); // Write an empty string to clear the file
                        fw.close();

                        System.out.println("Contents of the file have been cleared.");
                    } catch (IOException e) {
                        System.err.println("Error clearing the file: " + e.getMessage());
                    }

                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                }

                try {
                    FileWriter fw2 = new FileWriter(file);
                    BufferedWriter bw = new BufferedWriter(fw2);
                    for (int i = 0; i < listAccount.size(); i++) {

                        if (listAccount.get(i).getId() == senderAccountID) {
                            listAccount.get(i).withdraw(transferAmount);
                            bw.write(listAccount.get(i).toString());
                            bw.newLine();
                            //listAccount.remove(i);

                        } else if (listAccount.get(i).getId() == receiverAccountID) {
                            listAccount.get(i).deposit(transferAmount);
                            bw.write(listAccount.get(i).toString());
                            bw.newLine();
                            //listAccount.remove(i);
                        } else {
                            bw.write(listAccount.get(i).toString());
                            bw.newLine();
                        }
                    }
                    bw.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }


            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                Semaphore.release();
            }
        });
    }

        public static void main(String[] args) {
        //Start the server, if an exception occurs, print the stack trace
        WebServer server = new WebServer();
        int amount=500;
        int senderAccountID=123;
        int receiverAccountID=345;
        File file=new File("AccountInfo.txt");
        server.manageTextFileTransfer(file,senderAccountID,receiverAccountID,amount);
        try {
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

