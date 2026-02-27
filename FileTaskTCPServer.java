import java.io.*;
import java.net.Socket;

public class FileTaskTCPServer extends Thread{
    private Socket socket;
    public FileTaskTCPServer(Socket socket) {
        this.socket = socket; }
    public void run(){
        System.out.println("Client connected!");


        DataInputStream input = null;
        try {
            input = new DataInputStream(socket.getInputStream());

        OutputStream output = socket.getOutputStream();


        String fileName = input.readUTF();
        File file = new File("TestFile");

        if (file.exists()) {

            FileInputStream fileIn = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = fileIn.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            fileIn.close();
            System.out.println("File send.");
        } else {
            System.out.println("File not found.");
        }

        socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
