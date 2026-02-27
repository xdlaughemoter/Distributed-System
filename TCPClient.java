import java.io.*;
import java.net.*;

public class TCPClient {
    public static void main(String[] args) {
        try (Socket socket = new Socket("localhost", 5000)) {

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            InputStream in = socket.getInputStream();

            // 1. Vraag om bestand
            out.writeUTF("TestFile");

            // 2. Ontvang bestand en sla op
            FileOutputStream fileOut = new FileOutputStream("Test");
            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                fileOut.write(buffer, 0, bytesRead);
//                System.out.println("while");

            }
            System.out.println("File Received.!");
            fileOut.close();


        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}