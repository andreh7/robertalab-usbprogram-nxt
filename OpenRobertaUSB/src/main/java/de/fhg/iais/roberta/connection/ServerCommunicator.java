package de.fhg.iais.roberta.connection;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The server communicator emulates an EV3 brick. This class provides access to push requests, downloads the user program and download system libraries for
 * the upload funtion.
 *
 * @author dpyka
 */
public class ServerCommunicator {

    private String serverpushAddress;
    private String serverdownloadAddress;
    private String serverupdateAddress;

    private final CloseableHttpClient httpclient;
    private HttpPost post = null;

    private String filename = "";

    /**
     * @param serverAddress either the default address taken from the properties file or the custom address entered in the gui.
     */
    public ServerCommunicator(String serverAddress) {
        updateCustomServerAddress(serverAddress);
        this.httpclient = HttpClients.createDefault();
    }

    /**
     * Update the server address if the user wants to use an own installation of open roberta with a different IP address.
     *
     * @param customServerAddress for example localhost:1999 or 192.168.178.10:1337
     */
    public void updateCustomServerAddress(String customServerAddress) {
        this.serverpushAddress = customServerAddress + "/rest/pushcmd";
        this.serverdownloadAddress = customServerAddress + "/rest/download";
        this.serverupdateAddress = customServerAddress + "/rest/update";
    }

    /**
     * @return the file name of the last binary file downloaded of the server communicator object.
     */
    public String getFilename() {
        return this.filename;
    }

    /**
     * Sends a push request to the open roberta server for registration or keeping the connection alive. This will be hold by the server for approximately 10
     * seconds and then answered.
     *
     * @param requestContent data from the EV3 plus the token and the command send to the server (CMD_REGISTER or CMD_PUSH)
     * @return response from the server
     * @throws IOException if the server is unreachable for whatever reason.
     */
    public JSONObject pushRequest(JSONObject requestContent) throws IOException, JSONException {
        URL url = new URL("https://" + this.serverpushAddress);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setDoOutput(true);

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Accept-Charset", "UTF-8");

        OutputStream os = conn.getOutputStream();
        os.write(requestContent.toString().getBytes("UTF-8"));
        os.flush();
        os.close();

        if ( conn.getResponseCode() != HttpURLConnection.HTTP_OK ) {
            throw new IOException("Failed : HTTP error code : " + conn.getResponseCode());
        }

        InputStream responseEntity = new BufferedInputStream(conn.getInputStream());

        String responseText = "";
        if ( responseEntity != null ) {
            responseText = IOUtils.toString(responseEntity, "UTF-8");
        }

        responseEntity.close();
        conn.disconnect();

        return new JSONObject(responseText);
    }

    /**
     * Downloads a user program from the server as binary. The http POST is used here.
     *
     * @param requestContent all the content of a standard push request.
     * @return
     * @throws IOException if the server is unreachable or something is wrong with the binary content.
     */
    public byte[] downloadProgram(JSONObject requestContent) throws IOException {
        URL url = new URL("https://" + this.serverdownloadAddress);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setDoOutput(true);

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/octet-stream");
        conn.setRequestProperty("Accept-Charset", "UTF-8");
        OutputStream os = conn.getOutputStream();
        os.write(requestContent.toString().getBytes("UTF-8"));
        os.flush();
        os.close();

        if ( conn.getResponseCode() != HttpURLConnection.HTTP_OK ) {
            throw new IOException("Failed : HTTP error code : " + conn.getResponseCode());
        }

        InputStream responseEntity = new BufferedInputStream(conn.getInputStream());

        byte[] binaryfile = null;
        if ( responseEntity != null ) {
            this.filename = conn.getHeaderField("Filename");
            binaryfile = IOUtils.toByteArray(responseEntity);
        }

        responseEntity.close();
        conn.disconnect();

        return binaryfile;
    }

    /**
     * Basically the same as downloading a user program but without any information about the EV3. It uses http GET(!).
     *
     * @param fwFile name of the file in the url as suffix ( .../rest/update/ev3menu)
     * @return
     * @throws IOException if the server is unreachable or something is wrong with the binary content.
     */
    public byte[] downloadFirmwareFile(String fwFile) throws IOException {

        URL url = new URL("https://" + this.serverupdateAddress + "/" + fwFile);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

        conn.setDoInput(true);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/octet-stream");
        conn.setRequestProperty("Accept-Charset", "UTF-8");

        if ( conn.getResponseCode() != HttpURLConnection.HTTP_OK ) {
            throw new IOException("Failed : HTTP error code : " + conn.getResponseCode());
        }

        InputStream responseEntity = new BufferedInputStream(conn.getInputStream());

        byte[] binaryfile = null;
        if ( responseEntity != null ) {
            this.filename = conn.getHeaderField("Filename");
            binaryfile = IOUtils.toByteArray(responseEntity);
        }

        responseEntity.close();
        conn.disconnect();

        return binaryfile;
    }

    /**
     * Cancel a pending push request (which is blocking in another thread), if the user wants to disconnect.
     */
    public void abort() {
        if ( this.post != null ) {
            this.post.abort();
        }
    }

    /**
     * Shut down the http client.
     */
    public void shutdown() {
        try {
            this.httpclient.close();
        } catch ( IOException e ) {
            // ok
        }
    }
}
