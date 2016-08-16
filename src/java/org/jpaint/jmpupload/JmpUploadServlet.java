package org.jpaint.jmpupload;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class JmpUploadServlet extends HttpServlet {
    // <editor-fold defaultstate="collapsed" desc="members">
    
    protected String UploadDirectory;
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods">
    
    @Override
    public void init() 
            throws ServletException {
        
        super.init();
        
        UploadDirectory = getInitParameter("upload_directory");
        if (UploadDirectory == null ) {
            throw new ServletException("servet initalization parameter upload_directory not found");
        }
    }
    
    /**
     * Handles the HTTP
     * <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();
        try {
            /* TODO output your page here. You may use following sample code. */
            out.println("<!DOCTYPE html>");
            out.println("<html>");
            out.println("<head>");
            out.println("<title>JmpUpload</title>");
            out.println("</head>");
            out.println("<body>");
            out.println("<h1>This is an upload service endpoint</h1>");
            out.println("<ul>");
            out.println(String.format("<li>Request URL: %s</li>", request.getRequestURL()));
            out.println("</ul>");
            out.println("</body>");
            out.println("</html>");
        } finally {
            out.close();
        }
    }

    /**
     * Handles the HTTP
     * <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        JmpUploadRequest jmpUploadRequest = new JmpUploadRequest(UploadDirectory, request, response);
        jmpUploadRequest.processRequest();
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "JMP Upload Handler";
    }

    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="JmpUploadRequest class">
    
    public class JmpUploadRequest {
        
        public static final int OneMB = 1048576;
        public static final int ChunkSize = 3 * OneMB;
        public static final long MaxFileSize = Integer.MAX_VALUE;

        public String UploadDirectory;

        public HttpServletRequest Request;
        public HttpServletResponse Response;
        
        public String JmpMethod;

        public JmpUploadRequest(String uploadDirectory, HttpServletRequest request, HttpServletResponse response) {
            UploadDirectory = uploadDirectory;
            Request = request;
            Response = response;
        }

        public void processRequest() 
            throws ServletException, IOException {

            Response.setContentType("application/octet-stream");
            
            // handle upload method from client
            JmpMethod = Request.getHeader("JMP-METHOD");
            if ("BANDWIDTH.TEST".equals(JmpMethod)) {
                processBandwidthRequest();
                return;
            }
            else if ("OPEN.UPLOAD".equals(JmpMethod)) {
                processOpenUploadRequest();
                return;
            }
            else if ("UPLOAD.CHUNK".equals(JmpMethod)) {
                processUploadChunkRequest();
                return;
            }
            else if ("CLOSE.UPLOAD".equals(JmpMethod)) {
                processCloseUploadRequest();
                return;
            }
            
            // no or unknown upload method
            System.out.println("Client Request Error");
        }
        
        public void processBandwidthRequest()
            throws ServletException, IOException {
            
            long startTime = System.currentTimeMillis();
            ServletInputStream inputStream = Request.getInputStream();

            byte[] buffer = new byte[OneMB];
            int n = 0;
            int ntotal = 0;
            do {
                n = inputStream.read(buffer);
                ntotal += n;
            } while (n != -1);

            long elapsedTime = System.currentTimeMillis() - startTime;
            double bandwidth = 0;
            if (elapsedTime > 0) {
                bandwidth = (ntotal / (double) OneMB) / (elapsedTime / 1000.0);
            }

            System.out.println(String.format("BANDWIDTH.TEST bytes=%d bandwidth=%.2fMB/sec", ntotal, bandwidth));
        }
        
        public void processOpenUploadRequest()
            throws ServletException, IOException {
            
            int fileSize = 0;
            try {
                fileSize = Integer.parseInt(Request.getHeader("JMP-UPLOAD-FILE-SIZE"));
            }
            catch (NumberFormatException nfe) {
                Response.setStatus(400);
                return;
            }
            if (fileSize < 1 || fileSize > MaxFileSize) {
                Response.setStatus(400);
                return;
            }
            
            JmpUploadFileInfo finfo = new JmpUploadFileInfo(fileSize, ChunkSize);
            finfo.UploadId = UUID.randomUUID().toString();
            File uploadDirectory = new File(UploadDirectory);
            File dataFile = new File(uploadDirectory, finfo.UploadId);
            RandomAccessFile raf = new RandomAccessFile(dataFile, "rw");
            raf.setLength(finfo.FileSize);
            raf.close();
            
            Response.setHeader("JMP-UPLOAD-ID", finfo.UploadId);
            Response.setHeader("JMP-CHUNK-SIZE", Integer.toString(ChunkSize));
            
            System.out.println(String.format("OPEN.UPLOAD success UPLOAD-ID=%s size=%.2fMB/sec", finfo.UploadId, finfo.FileSizeMB));
        }
        
        public void processUploadChunkRequest()
            throws ServletException, IOException {
            
            String uploadId = Request.getHeader("JMP-UPLOAD-ID");
            if (uploadId == null) {
                System.out.println("UPLOAD.CHUNK error JMP-UPLOAD-ID not specified");
                Response.setStatus(400);
                return;
            }
            
            int chunkNum = 0;
            try {
                chunkNum = Integer.parseInt(Request.getHeader("JMP-CHUNK-NUMBER"));
            }
            catch (NumberFormatException nfe) {
                System.out.println("UPLOAD.CHUNK error JMP-CHUNK-NUMBER not specified");
                Response.setStatus(400);
                return;
            }
            
            File uploadDirectory = new File(UploadDirectory);
            File dataFile = new File(uploadDirectory, uploadId);
            RandomAccessFile raf = new RandomAccessFile(dataFile, "rw");
            long fileSize = dataFile.length();
            JmpUploadFileInfo finfo = new JmpUploadFileInfo(fileSize, ChunkSize);
            finfo.UploadId = uploadId;
            
            if (chunkNum < 0 || chunkNum >= finfo.NumChunks) {
                System.out.println(String.format("UPLOAD.CHUNK error JMP-CHUNK-NUMBER=%d out of range", chunkNum));
                Response.setStatus(400);
                return;
            }
            
            long startTime = System.currentTimeMillis();
            ServletInputStream inputStream = Request.getInputStream();

            int chunkSize = finfo.getChunkSize(chunkNum);
            raf.seek(finfo.chunkSeekOffset(chunkNum));
            byte[] buffer = new byte[OneMB];
            int n = 0;
            int ntotal = 0;
            do {
                n = inputStream.read(buffer);
                if (n != -1) {   
                    ntotal += n;
                    raf.write(buffer, 0, n);
                }
            } while (n != -1 && ntotal < chunkSize);
            
            raf.close();
            
            long elapsedTime = System.currentTimeMillis() - startTime;
            double bandwidth = 0;
            if (elapsedTime > 0) {
                bandwidth = (ntotal / (double) OneMB) / (elapsedTime / 1000.0);
            }

            double sizeMB = (double) ntotal / (double) OneMB;
            System.out.println(String.format("UPLOAD.CHUNK UPLOAD-ID=%s size=%.2fMB bandwidth=%.2fMB/sec", finfo.UploadId, sizeMB, bandwidth));
        }
        
        public void processCloseUploadRequest()
            throws ServletException, IOException {
            
            String uploadId = Request.getHeader("JMP-UPLOAD-ID");
            if (uploadId == null) {
                System.out.println("CLOSE.UPLOAD error JMP-UPLOAD-ID not specified");
                Response.setStatus(400);
                return;
            }
            
            System.out.println(String.format("CLOSE.UPLOAD complete JMP-UPLOAD-ID=%s", uploadId));
        }
        
        public class JmpUploadFileInfo {
            
            public String UploadId;
            public int ChunkSize;
            public long FileSize;
            public double FileSizeMB;
            public int NumChunks;
            public int LastChunkSize;
            
            public JmpUploadFileInfo (long fileSize, int chunkSize) {
                FileSize = fileSize;
                ChunkSize = chunkSize;
                FileSizeMB = (double) FileSize / (double) OneMB;
                NumChunks = (int) Math.floor((double) FileSize / (double) ChunkSize);
                LastChunkSize = (int) (FileSize % ChunkSize);
                if (LastChunkSize > 0) {
                    NumChunks++;
                }
            }
            
            public boolean isFirstChunk(int chunkNum) {
                return chunkNum == 0;
            }
            
            public boolean isLastChunk(int chunkNum) {
                return chunkNum == (NumChunks - 1);
            }
            
            public long chunkSeekOffset(int chunkNum) {
                return chunkNum * ChunkSize;
            }
            
            public int getChunkSize(int chunkNum) {
                if (isLastChunk(chunkNum)) {
                    return LastChunkSize;
                }
                return ChunkSize;
            }
        }
        
    }
    
    // </editor-fold>
}
