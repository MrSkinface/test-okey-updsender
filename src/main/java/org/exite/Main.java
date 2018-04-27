package org.exite;

import org.apache.log4j.Logger;
import org.exite.RestExAPI.IRestExAPI;
import org.exite.RestExAPI.RestExAPI;
import org.exite.crypt.CryptEx;
import org.exite.crypt.ESignType;
import org.exite.obj.DocumentType;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.UUID;

/**
 * Created by levitsky on 27.04.18.
 */
public class Main {

    static int counter;
    static int threadsCount;

    static byte[]body;

    static IRestExAPI rest = new RestExAPI();
    static String authToken;
    static CryptEx cryptex;
    static String cryptexAlias;

    private static final Logger log = Logger.getLogger(Main.class);

    public Main(String login, String pass, int counter, int threadsCount, String srcFilePath, String cryptexAlias) throws Exception {

        this.counter = counter;
        this.threadsCount = threadsCount;
        this.cryptexAlias = cryptexAlias;

        try{

            this.authToken = rest.authorize(login, pass);
            cryptex = new CryptEx(null);
            this.body = Files.readAllBytes(Paths.get(srcFilePath));

            for(int i=0;i<threadsCount;i++){
                new Thread(new Generator(rest, i)).start();
            }
        } catch (Exception e){
            log.error(e);
        }
    }



    public static void main(String[] args) throws Exception {
        String login = args[0];
        String pass = args[1];
        int counter = Integer.parseInt(args[2]);
        int threadsCount = Integer.parseInt(args[3]);
        String filePath = args[4];
        String cryptexAlias = args[5];
        new Main(login, pass, counter, threadsCount, filePath, cryptexAlias);
    }
}

class Generator implements Runnable {

    private static final Logger log = Logger.getLogger(Generator.class);

    IRestExAPI rest;
    int curThread;

    public Generator(IRestExAPI rest, int curThread) {
        this.rest = rest;
        this.curThread = curThread;
    }

    @Override
    public void run() {
        try{

            for(int i=(Main.counter/Main.threadsCount)*curThread;i<((Main.counter/Main.threadsCount)*curThread)+(Main.counter/Main.threadsCount);i++){
                generateTest(i+1);
            }

        } catch (Exception e){
            log.error(e);
        }
    }

    private void generateTest(int counter) throws Exception {
        try{
            byte[] body = modifyBody(counter);
            byte[] sign = getSign(body);
            final String stringBody = Base64.getEncoder().encodeToString(body);
            final String stringSign = Base64.getEncoder().encodeToString(sign);
            final String filename = getFileName(body);
            int code = rest.sendDocument(Main.authToken, stringBody, stringSign, DocumentType.ON_SCHFDOPPR);
            log.info("Sent to api " + filename + " with http code " + code);
        } catch (Exception e){
            e.printStackTrace();
            log.error(e);
        }
    }

    private byte[] modifyBody(int counter) throws Exception {
        /*
        *
        * */
        SAXBuilder builder = new SAXBuilder();
        Document doc = builder.build(new ByteArrayInputStream(Main.body));
        Attribute id=doc.getRootElement().getAttribute("ИдФайл");
        id.setValue(
                id.getValue().replace(id.getValue().split("_")[5], UUID.randomUUID().toString())
        );
        Attribute num = doc.getRootElement().getChild("Документ").getChild("СвСчФакт").getAttribute("НомерСчФ");
        num.setValue(num.getValue()+"-"+counter);
        XMLOutputter out =new XMLOutputter();
        out.setFormat(Format.getPrettyFormat().setEncoding("windows-1251"));
        ByteArrayOutputStream baos=new ByteArrayOutputStream();
        out.output(doc, baos);
        final byte[] res = baos.toByteArray();
        baos.close();
        return res;
    }

    private String getFileName(byte[] body) throws Exception {
        SAXBuilder builder = new SAXBuilder();
        Document doc = builder.build(new ByteArrayInputStream(body));
        return doc.getRootElement().getAttribute("ИдФайл").getValue();
    }


    private byte[] getSign(final byte[] body) throws Exception {
        return Main.cryptex.signCAdES(body, Main.cryptexAlias, ESignType.DER);
    }

}
