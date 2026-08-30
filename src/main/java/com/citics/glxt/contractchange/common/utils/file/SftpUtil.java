package com.citics.glxt.common.utils.file;

import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;
import org.omg.CORBA.SystemException;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Properties;

@Slf4j
public class SftpUtil {
    private String host;
    private int port;
    private ChannelSftp sftp = null;
    private Channel channel = null;
    private Session session = null;

    private SftpUtil(String host, int port){
        this.host = host;
        this.port = port;
    }

    public static SftpUtil create(String host, int port) throws JSchException {
        return new SftpUtil(host,port);
    }

    /**
     * 增加encoding支持
     * 此版本的jsch不支持设置编码格式，因此使用反射方式写入
     *
     * @author wangzhe
     */
    public void connect(String username, String password, String encoding) throws Exception {
        Long startTime = System.currentTimeMillis();
        JSch jsch = new JSch();
        session = jsch.getSession(username, host, port);
        session.setPassword(password);
        Properties config = new Properties();
        config.put("kex", "diffie-hellman-group1-sha1");
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.connect();
        channel = session.openChannel("sftp");
        channel.connect();
        sftp = (ChannelSftp) channel;
        if (encoding.equals("GBK") || encoding.equals("gbk")) {
            encoding = "GBK";
        } else {
            encoding = "UTF-8";
        }
        Class cl = sftp.getClass();
        Field field = cl.getDeclaredField("server_version");
        field.setAccessible(true);
        field.set(sftp, 2);
        sftp.setFilenameEncoding(encoding);
        Long endTime = System.currentTimeMillis();
        log.info("连接sftp成功，耗时{}毫秒", endTime-startTime);
    }

    /*public void upload(InputStream inputStream, String filepath) throws SftpException, IOException {
        File file = new File(filepath);
        String dirname = file.getParent();
        mkdir(dirname);
        sftp.put(inputStream, filepath);
        inputStream.close();
    }*/

    public void upload(String src, String dst) throws SftpException {
        sftp.put(src, dst, (SftpProgressMonitor)null, 0);
    }

    public void mkdir(String dirname) throws SftpException {
        String workDir = sftp.pwd();
        try {
            dirname = dirname.replace('\\', '/');
            dirname = dirname.replaceAll("/+", "/");
            try{
                sftp.cd(dirname);
                return ;
            }catch(Exception e){

            }
            if(dirname.endsWith("/")){
                dirname = dirname.substring(0, dirname.length() - 1);
            }
            if(dirname.startsWith("/")){
                dirname = dirname.substring(1);
                sftp.cd("/");
            }
            String[] dirs = dirname.split("/");
            for(int i = 0; i < dirs.length; ++i) {
                String dir = dirs[i];
                if(null == dir || dir.isEmpty()){
                    continue;
                }
                try {
                    sftp.cd(dir);
                }catch(Exception e) {
                    e.printStackTrace();
                    sftp.mkdir(dir);
                    try {
                        sftp.cd(dir);
                    }catch(Exception e1) {
                        e1.printStackTrace();
                        throw new SftpException(-1, "create dir failed");
                    }
                }
            }
        }finally{
            sftp.cd(workDir);
        }
    }

    /**
     * 判断目录是否存在
     **/
    public boolean isDirExist(String directory) {
        boolean isDirExistFlag = false;
        try {
            SftpATTRS sftpATTRS = sftp.lstat(directory);
            log.info("目录"+directory+"已存在");
            isDirExistFlag = true;
            return sftpATTRS.isDir();
        } catch (Exception e) {
            if (e.getMessage().toLowerCase().equals("no such file")) {
                log.info("目录"+directory+"不存在");
                isDirExistFlag = false;
            }
        }
        return isDirExistFlag;
    }

    /**
     * 创建目录
     **/
    public void createDir(String createpath) throws SystemException {
        try {
            String pathArry[] = createpath.split("/");
            StringBuffer filePath = new StringBuffer("/");
            for (String path : pathArry) {
                if (path.equals("")) {
                    continue;
                }
                filePath.append(path + "/");
                if (isDirExist(filePath.toString())) {
                    sftp.cd(filePath.toString());
                } else {
                    // 建立目录
                    sftp.mkdir(filePath.toString());
                    log.info("创建目录"+filePath.toString()+"成功");
                    // 进入并设置为当前目录
                    sftp.cd(filePath.toString());
                    log.info("进入目录"+filePath.toString());
                }
            }
            sftp.cd(createpath);
        } catch (SftpException e) {
            //throw new SystemException("创建路径错误：" + createpath);
        }
    }

    public void disconnect(){
        if(sftp!=null && sftp.isConnected()){
            sftp.disconnect();
        }
        if(channel!=null && channel.isConnected()){
            channel.disconnect();
        }
        if(session!=null && session.isConnected()){
            session.disconnect();
        }
    }


    public InputStream download(String filename) throws SftpException {
        return sftp.get(filename);
    }
}
