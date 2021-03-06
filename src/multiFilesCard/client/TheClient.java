package client;

import java.util.Date;
import java.io.*;
import java.security.MessageDigest;

import opencard.core.service.*;
import opencard.core.terminal.*;
import opencard.core.util.*;
import opencard.opt.util.*;


public class TheClient {

	private PassThruCardService servClient = null;
	boolean DISPLAY = false;
	boolean loop = true;

	static final byte CLA					= (byte)0x90;
	static final byte P1					= (byte)0x00;
	static final byte P2					= (byte)0x00;

	static final byte UPDATECARDKEY				= (byte)0x06;
	static final byte UNCIPHERFILEBYCARD		= (byte)0x05;
	static final byte CIPHERFILEBYCARD			= (byte)0x04;
	static final byte READFILEFROMCARD			= (byte)0x03;
	static final byte WRITEFILETOCARD			= (byte)0x02;
	static final byte LISTINGFILE			= (byte)0x01;

	final static short MAXLENGTH = 255;
	final static short CIPHER_MAXLENGTH = 240;
	
	static final byte P1_FILENAME 	 	= (byte)0x01;
	static final byte P1_BLOC 	 		= (byte)0x02;
	static final byte P1_VAR 	 		= (byte)0x03;
	static final byte P1_LASTBLOCK 	 	= (byte)0x04;
	static final byte P1_NBFILES		= (byte)0x05;
	static final byte P1_FILEINFO		= (byte)0x06;
	static final byte P1_OFFSET 	 	= (byte)0x07;
	
	static 	byte[] dataBlock = new byte[MAXLENGTH];
	static 	byte[] cipherdataBlock = new byte[CIPHER_MAXLENGTH];


    private final static byte INS_DES_ECB_NOPAD_ENC           	= (byte)0x20;
    private final static byte INS_DES_ECB_NOPAD_DEC           	= (byte)0x21;

	public TheClient() {
		try {
			SmartCard.start();
			System.out.print( "Smartcard inserted?... " ); 

			CardRequest cr = new CardRequest (CardRequest.ANYCARD,null,null); 

			SmartCard sm = SmartCard.waitForCard (cr);

			if (sm != null) {
				System.out.println ("got a SmartCard object!\n");
			} else
				System.out.println( "did not get a SmartCard object!\n" );

			this.initNewCard( sm ); 

			SmartCard.shutdown();

		} catch( Exception e ) {
			System.out.println( "TheClient error: " + e.getMessage() );
		}
		java.lang.System.exit(0) ;
	}

	private ResponseAPDU sendAPDU(CommandAPDU cmd) {
		return sendAPDU(cmd, true);
	}

	private ResponseAPDU sendAPDU( CommandAPDU cmd, boolean display ) {
		ResponseAPDU result = null;
		try {
			result = this.servClient.sendCommandAPDU( cmd );
			if(display)
				displayAPDU(cmd, result);
		} catch( Exception e ) {
			System.out.println( "Exception caught in sendAPDU: " + e.getMessage() );
			java.lang.System.exit( -1 );
		}
		return result;
	}


	/************************************************
	 * *********** BEGINNING OF TOOLS ***************
	 * **********************************************/


	private String apdu2string( APDU apdu ) {
		return removeCR( HexString.hexify( apdu.getBytes() ) );
	}


	public void displayAPDU( APDU apdu ) {
		System.out.println( removeCR( HexString.hexify( apdu.getBytes() ) ) + "\n" );
	}


	public void displayAPDU( CommandAPDU termCmd, ResponseAPDU cardResp ) {
		System.out.println( "--> Term: " + removeCR( HexString.hexify( termCmd.getBytes() ) ) );
		System.out.println( "<-- Card: " + removeCR( HexString.hexify( cardResp.getBytes() ) ) );
	}


	private String removeCR( String string ) {
		return string.replace( '\n', ' ' );
	}


	/******************************************
	 * *********** END OF TOOLS ***************
	 * ****************************************/


	private boolean selectApplet() {
		boolean cardOk = false;
		try {
			CommandAPDU cmd = new CommandAPDU( new byte[] {
				(byte)0x00, (byte)0xA4, (byte)0x04, (byte)0x00, (byte)0x0A,
				    (byte)0xA0, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x62, 
				    (byte)0x03, (byte)0x01, (byte)0x0C, (byte)0x06, (byte)0x01
			} );
			ResponseAPDU resp = this.sendAPDU( cmd );
			if( this.apdu2string( resp ).equals( "90 00" ) )
				cardOk = true;
		} catch(Exception e) {
			System.out.println( "Exception caught in selectApplet: " + e.getMessage() );
			java.lang.System.exit( -1 );
		}
		return cardOk;
	}


	private void initNewCard( SmartCard card ) {
		if( card != null )
			System.out.println( "Smartcard inserted\n" );
		else {
			System.out.println( "Did not get a smartcard" );
			System.exit( -1 );
		}

		System.out.println( "ATR: " + HexString.hexify( card.getCardID().getATR() ) + "\n");


		try {
			this.servClient = (PassThruCardService)card.getCardService( PassThruCardService.class, true );
		} catch( Exception e ) {
			System.out.println( e.getMessage() );
		}

		System.out.println("Applet selecting...");
		if( !this.selectApplet() ) {
			System.out.println( "Wrong card, no applet to select!\n" );
			System.exit( 1 );
			return;
		} else 
			System.out.println( "Applet selected" );

		mainLoop();
	}

	void compareFile(){
		System.out.println("Select first file: ");
		String path1 = readKeyboard();
		System.out.println("Select second file: ");
		String path2 = readKeyboard();

		File file1 = new File(path1);
		File file2 = new File(path2);
		try {
			MessageDigest md5Digest = MessageDigest.getInstance("MD5");
			MessageDigest shaDigest = MessageDigest.getInstance("SHA-256");
	
			String md5checksum1 = getFileChecksum(md5Digest, file1);
			String shaChecksum1 = getFileChecksum(shaDigest, file1);
			String md5checksum2 = getFileChecksum(md5Digest, file2);
			String shaChecksum2 = getFileChecksum(shaDigest, file2);
			
			System.out.println("MD5:\t"+path1+": " + md5checksum1 + "\t"+path2+": "+md5checksum2);
			System.out.println("SHA-256:\t"+path1+": " + shaChecksum1 + "\t"+path2+": "+shaChecksum2);

			if(md5checksum1.equals(md5checksum2) && shaChecksum1.equals(shaChecksum2)){
				System.out.println("Files content are equals");
			}else{
				System.out.println("Files content are not equals !");
			}

		} catch (Exception e) {
			System.out.println(e.getStackTrace());
		}
		

	}


	void updateCardKey() {
		int dataSize =0;
		String DESKey = "";
		do{
		System.out.println("Select DES Key (8 bytes):");
		DESKey = readKeyboard();
		dataSize = DESKey.getBytes().length;
		}while(dataSize != 8);

		byte[] data = new byte[dataSize];
		System.arraycopy(DESKey.getBytes(), (byte)0, data, (byte)0, (byte)dataSize);

		byte[] header = {CLA,UPDATECARDKEY, P1,P2}; 
		byte[] optional = new byte[(byte)(dataSize+2)];
		optional[0] = (byte)dataSize;
		optional[dataSize+1] = 0x00; // pour etre sur que la DES key s'est bien update ! (echo)
		System.arraycopy(data,(byte)0,optional,(byte)1,(byte)dataSize);
		byte[] command = new byte[(byte)header.length + (byte)optional.length];
		System.arraycopy(header,(byte)0,command,(byte)0,(byte)header.length);
		System.arraycopy(optional,(byte)0,command,(byte)header.length,(byte)optional.length);
		CommandAPDU cmd = new CommandAPDU( command);
		ResponseAPDU resp = this.sendAPDU( cmd, DISPLAY );		
	}


	void uncipherFileByCard() {
		System.out.println("Select input file (cipher): ");
		String inputfilename = readKeyboard();
		System.out.println("Select output file (plaintext): ");
		String outputfilename = readKeyboard();		
		byte[] response;


		try{
				DataInputStream filedata = new DataInputStream(new FileInputStream(inputfilename));
				DataOutputStream uncipherdata = new DataOutputStream(new FileOutputStream(outputfilename));
			
				int return_value = 0;

				while( (return_value = filedata.read(cipherdataBlock,0,CIPHER_MAXLENGTH)) !=-1 ) {
					if(return_value == CIPHER_MAXLENGTH){
						response = cipherGeneric(UNCIPHERFILEBYCARD,INS_DES_ECB_NOPAD_DEC, cipherdataBlock);
						uncipherdata.write(response, 0, return_value);
					}else{
						// extration du bon bout
						byte[] finalData = new byte[return_value];
						System.arraycopy(cipherdataBlock, (byte)0, finalData, (byte)0, return_value);
						// uncipher
						response = cipherGeneric(UNCIPHERFILEBYCARD,INS_DES_ECB_NOPAD_DEC, finalData);
						// retirer padding
						int padding_extrait = (response[return_value-1]-48); //(-48 pour offset dans la table ASCII)
						uncipherdata.write(response, 0, return_value-padding_extrait);
							
					}

				}


			}catch(Exception e){
				System.out.println(e);
			}		
	}


	void cipherFileByCard() {
		System.out.println("Select input file (plaintext): ");
		String inputfilename = readKeyboard();
		System.out.println("Select output file (cipher): ");
		String outputfilename = readKeyboard();
		byte[] response;


		try{
				DataInputStream filedata = new DataInputStream(new FileInputStream(inputfilename));
				DataOutputStream cipherdata = new DataOutputStream(new FileOutputStream(outputfilename));
			
				int return_value = 0;

				while( (return_value = filedata.read(cipherdataBlock,0,CIPHER_MAXLENGTH)) !=-1 ) {

					if(return_value == CIPHER_MAXLENGTH){
						response = cipherGeneric(CIPHERFILEBYCARD,INS_DES_ECB_NOPAD_ENC, cipherdataBlock);
						
					}else{

						 int paddingSize = (8-(return_value%8));
						 byte[] finalData = new byte[return_value+paddingSize];
						 
						 byte[] finalPadding = new byte[paddingSize];
						 for(int i =0; i < paddingSize ; i++){
						 finalPadding[i]= (byte)(paddingSize+48); //(+48 pour offset dans la table ASCII)
						 }

						 System.arraycopy(cipherdataBlock, (byte)0, finalData, (byte)0, return_value);
						 System.arraycopy(finalPadding, (byte)0, finalData,return_value,paddingSize);

						 response = cipherGeneric(CIPHERFILEBYCARD,INS_DES_ECB_NOPAD_ENC, finalData);
					}
					cipherdata.write(response);
				}

			}catch(Exception e){
				System.out.println(e);
			}		
	}

	void readFileFromCard() {

		String input ="";
		int nbFiles = 0;
		int filerequested = 0;


						/*Get Number of File*/
						byte[] command0 = {CLA,LISTINGFILE, P1_NBFILES,P2,0x00};
						CommandAPDU cmd0 = new CommandAPDU( command0);
						ResponseAPDU resp0 = this.sendAPDU( cmd0, DISPLAY );
				
						byte[] bytes0 = resp0.getBytes();
						String msg = "";
						for(int i=0; i<bytes0.length-2;i++)
							msg += new StringBuffer("").append((char)(bytes0[i]+48));
						if(DISPLAY) System.out.println("Number of Files: "+msg);
				
						nbFiles =Integer.parseInt(msg);
						/*end*/

						if(nbFiles == 0){
							System.out.println("Error: No file onboard !");
							return;
						}

				do{
					System.out.println("Select your file (number):");
					input = readKeyboard();
				}while(!isNumeric(input));
				filerequested = Integer.parseInt(input);


				if(filerequested<0 || filerequested>(nbFiles-1)){
					System.out.println("Error invalid file number");
					return;
				}

				/* Send OFFSET to Applet */
				if(DISPLAY) System.out.println("==========Requete: Sending OFFSET==========");
				byte[] command = {CLA,READFILEFROMCARD, P1_OFFSET,(byte)filerequested}; 
				CommandAPDU cmd = new CommandAPDU( command);
				ResponseAPDU resp = this.sendAPDU( cmd, DISPLAY );
				if(DISPLAY) System.out.println("==========Fin Requete: Sending OFFSET==========");
				/* end */


		/* Read filename */
		if(DISPLAY) System.out.println("==========Requete: Filename==========");
		byte[] header10 = {CLA,READFILEFROMCARD, P1_FILENAME,P2}; 
		byte[] optional10 = {0x00};
		byte[] command10 = new byte[(byte)header10.length + (byte)optional10.length];
		System.arraycopy(header10,(byte)0,command10,(byte)0,(byte)header10.length);
		System.arraycopy(optional10,(byte)0,command10,(byte)header10.length,(byte)optional10.length);
		CommandAPDU cmd10 = new CommandAPDU( command10);
		ResponseAPDU resp10 = this.sendAPDU( cmd10, DISPLAY );
		if(DISPLAY) System.out.println("==========Fin Requete: Filename==========");
		/* end */

		byte[] bytes10 = resp10.getBytes();
		String filename = "";
	    for(int i=0; i<bytes10.length-2;i++)
		filename += new StringBuffer("").append((char)bytes10[i]);



		/* Read  Valeurs Variables nbAPDUMax et lastAPDUsize */
		if(DISPLAY) System.out.println("==========Requete: Valeurs Variables==========");
		byte[] header1 = {CLA,READFILEFROMCARD, P1_VAR,P2}; 
		byte[] optional1 = {0x00};
		byte[] command1 = new byte[(byte)header1.length + (byte)optional1.length];
		System.arraycopy(header1,(byte)0,command1,(byte)0,(byte)header1.length);
		System.arraycopy(optional1,(byte)0,command1,(byte)header1.length,(byte)optional1.length);
		CommandAPDU cmd1 = new CommandAPDU( command1);
		ResponseAPDU resp1 = this.sendAPDU( cmd1, DISPLAY );
		if(DISPLAY) System.out.println("==========Fin Requete: Valeurs Variables==========");
		/* end */

		byte[] bytes1 = resp1.getBytes();
		int nbAPDUMax = (short)((short)bytes1[0]&(short)255);
		int lastAPDUsize = (short)((short)bytes1[1]&(short)255);




		try{
			DataOutputStream filedata = new DataOutputStream(new FileOutputStream("retour_"+filename));			


			for(int indice = 0 ; indice < nbAPDUMax; indice++)
			{

				/* Requete bloc d'indice P2*/
				if(DISPLAY) System.out.println("==========Requete: Bloc==========");
				byte[] command2 = {CLA,READFILEFROMCARD, P1_BLOC,(byte)indice,0x00}; 
				CommandAPDU cmd2 = new CommandAPDU( command2);
				ResponseAPDU resp2 = this.sendAPDU( cmd2, DISPLAY );
				if(DISPLAY) System.out.println("==========Fin Requete: Bloc==========");
				/* end */


				byte[] block = resp2.getBytes();
				
				// A mettre dans le fichier
				filedata.write(block, 0, (block.length-2));
			}

				/* Requete dernier bloc*/
				if(DISPLAY) System.out.println("==========Requete: Last Bloc==========");
				byte[] command2 = {CLA,READFILEFROMCARD, P1_LASTBLOCK,P2,0x00}; 
				CommandAPDU cmd2 = new CommandAPDU( command2);
				ResponseAPDU resp2 = this.sendAPDU( cmd2, DISPLAY );
				if(DISPLAY) System.out.println("==========Fin Requete: Last Bloc==========");
				/* end */		
				byte[] block = resp2.getBytes();
				
				// A mettre dans le fichier
				filedata.write(block,0, (block.length-2));

		}catch(Exception e){
			System.out.println(e);
		}
	}


	void writeFileToCard() {
		System.out.println("Select your file:");
		String filename = readKeyboard();
		byte filenameSize = (byte)filename.getBytes().length;
		int nbAPDUMax = 0;
		int lastAPDUsize = 0;

		/* envoi size filename et filename */
		if(DISPLAY) System.out.println("==========Requete: Filename==========");
		byte[] header = {CLA,WRITEFILETOCARD, P1_FILENAME,P2}; // requete de type "filename" ( contient la taille de filename et filename)
		byte[] optional = new byte[(byte)1 + filenameSize];
		byte[] command = new byte[header.length + optional.length];
		optional[0] = filenameSize;
		System.arraycopy(filename.getBytes(), (byte)0, optional, (byte)1, filenameSize);
		System.arraycopy(header,(byte)0,command,(byte)0,header.length);
		System.arraycopy(optional,(byte)0,command,header.length,optional.length);
		CommandAPDU cmd = new CommandAPDU( command);
		ResponseAPDU resp = this.sendAPDU( cmd, DISPLAY );
		if(DISPLAY)System.out.println("==========Fin Requete: Filename==========");
		/* end */

	
		try{
			DataInputStream filedata = new DataInputStream(new FileInputStream(filename));
		
		int return_value = 0;

		while( (return_value = filedata.read(dataBlock,0,MAXLENGTH)) !=-1 ) {
			if(return_value == MAXLENGTH){
				nbAPDUMax ++;
				/* envoi d'un bloc */
				if(DISPLAY) System.out.println("==========Requete: Bloc==========");
				byte[] header1 = {CLA,WRITEFILETOCARD,P1_BLOC,(byte)(nbAPDUMax-1)}; // requete de type "bloc" (contient un bloc de 255 octets) avec P2 = indice du bloc
				byte[] optional1 = new byte[(return_value+1)];
				byte[] command1 = new byte[header1.length +optional1.length];
				optional1[0] =  (byte)return_value;
				System.arraycopy(dataBlock, (byte)0, optional1, (byte)1, return_value);
				System.arraycopy(header1,(byte)0,command1,(byte)0,header1.length);
				System.arraycopy(optional1,(byte)0,command1,header1.length,optional1.length);
				CommandAPDU cmd1 = new CommandAPDU( command1);
				ResponseAPDU resp1 = this.sendAPDU( cmd1, DISPLAY );
				if(DISPLAY) System.out.println("==========Fin Requete: Bloc==========");
				/* end */


			}else{

				lastAPDUsize = return_value;
				/* envoi du DERNIER bloc */
				if(DISPLAY) System.out.println("==========Requete: Bloc==========");
				byte[] header2 = {CLA,WRITEFILETOCARD,P1_BLOC,(byte)nbAPDUMax}; // requete de type "bloc" (contient un bloc de lastAPDUsize octets) avec P2 = indice du bloc
				byte[] optional2 = new byte[(byte)1 + lastAPDUsize];
				byte[] command2 = new byte[header2.length + optional2.length];
				optional2[0] = (byte)lastAPDUsize;
				System.arraycopy(dataBlock, (byte)0, optional2, (byte)1, lastAPDUsize);
				System.arraycopy(header2,(byte)0,command2,(byte)0,header2.length);
				System.arraycopy(optional2,(byte)0,command2,header2.length,optional2.length);
				CommandAPDU cmd2 = new CommandAPDU( command2);
				ResponseAPDU resp2 = this.sendAPDU( cmd2, DISPLAY );
				if(DISPLAY) System.out.println("==========Fin Requete: Bloc==========");
				/* end */

				System.out.println("nbAPDUMax :"+nbAPDUMax+"; lastAPDUsize :"+lastAPDUsize+"; Total length: "+(nbAPDUMax*MAXLENGTH+lastAPDUsize)+"bytes");

				/* envoi des valeurs */
				if(DISPLAY) System.out.println("==========Requete: Valeurs Variables==========");
				byte[] header3 = {CLA,WRITEFILETOCARD,P1_VAR,P2}; // requete de type "var" (contient nbAPDUMax et lastAPDUsize)
				byte[] optional3 = {(byte)0x02,(byte)nbAPDUMax,(byte)lastAPDUsize};
				byte[] command3 = new byte[header3.length +optional3.length];
				System.arraycopy(header3,(byte)0,command3,(byte)0,header3.length);
				System.arraycopy(optional3,(byte)0,command3,header3.length,optional3.length);
				CommandAPDU cmd3 = new CommandAPDU( command3);
				ResponseAPDU resp3 = this.sendAPDU( cmd3, DISPLAY );
				/* end */
				if(DISPLAY) System.out.println("==========Fin Requete: Valeurs Variables==========");

			}

		}

		}catch(Exception e){
			System.out.println(e);
		}
	}

	void listingFile(){

		byte[] command = {CLA,LISTINGFILE, P1_NBFILES,P2,0x00};
		CommandAPDU cmd = new CommandAPDU( command);
		ResponseAPDU resp = this.sendAPDU( cmd, DISPLAY );

		byte[] bytes = resp.getBytes();
		String msg = "";
	    for(int i=0; i<bytes.length-2;i++)
		    msg += new StringBuffer("").append((char)(bytes[i]+48));
			if(DISPLAY) System.out.println("Number of Files: "+msg);

		int nbFiles =Integer.parseInt(msg);

		for (int i = 0; i < nbFiles; i++) {

			byte[] commandi = {CLA,LISTINGFILE, P1_FILEINFO, (byte)i, 0x00 };
			CommandAPDU cmdi = new CommandAPDU( commandi);
			ResponseAPDU respi = this.sendAPDU( cmdi, DISPLAY );

			byte[] bytesi = respi.getBytes();
			System.out.print("File number: "+bytesi[0]+"\t size: "+((((short)((short)bytesi[1]&(short)255))*MAXLENGTH)+((short)bytesi[2]&(short)255))+" bytes\t filename: ");
			msg = "";
	    	 for(int j=4; j<4+bytesi[3];j++)
		     msg += new StringBuffer("").append((char)(bytesi[j]));
			 System.out.println(msg);

		}	
	}

	void exit() {
		loop = false;
	}

	void runAction( int choice ) {
		switch( choice ) {
			case 7: compareFile(); break;
			case 6: updateCardKey(); break;
			case 5: uncipherFileByCard(); break;
			case 4: cipherFileByCard(); break;
			case 3: readFileFromCard(); break;
			case 2: writeFileToCard(); break;
			case 1: listingFile(); break;
			case 0: exit(); break;
			default: System.out.println( "unknown choice!" );
		}
	}


	String readKeyboard() {
		String result = null;

		try {
			BufferedReader input = new BufferedReader( new InputStreamReader( System.in ) );
			result = input.readLine();
		} catch( Exception e ) {}

		return result;
	}


	int readMenuChoice() {
		int result = 0;

		try {
			String choice = readKeyboard();
			result = Integer.parseInt( choice );
		} catch( Exception e ) {}

		System.out.println( "" );

		return result;
	}


	void printMenu() {
		System.out.println( "" );
		System.out.println( "7: Compare two files" );
		System.out.println( "6: Changer DES key" );
		System.out.println( "5: Uncipher file using DES" );
		System.out.println( "4: Cipher file using DES" );
		System.out.println( "3: Read file from card" );
		System.out.println( "2: Write file to card" );
		System.out.println( "1: Listing Files" );
		System.out.println( "0: Quitter" );
		System.out.print( "--> " );
	}


    private byte[] cipherGeneric(byte INS, byte typeINS, byte[] challenge ) {
		byte[] result = new byte[challenge.length];

		/* Forgage de la requete pour cippher/uncipher*/
		byte[] header = {CLA,INS, typeINS,(byte)0x00};
		byte[] optional = new byte[(2+challenge.length)];
		optional[0] = (byte)challenge.length;
		System.arraycopy(challenge, 0, optional, (byte)1, ((short)optional[0]&(short)255));
		byte[] command = new byte[header.length + optional.length];
		System.arraycopy(header, (byte)0, command, (byte)0, header.length);
		System.arraycopy(optional, (byte)0, command,header.length, optional.length);
		CommandAPDU cmd = new CommandAPDU( command);

		/*end Requete*/

		/* Reception et retour du cipher */
		ResponseAPDU resp = this.sendAPDU( cmd, DISPLAY );
		byte[] bytes = resp.getBytes();
		System.arraycopy(bytes, 0, result, 0, (short)((short)(bytes.length-2)&(short)255));
		return result;		
	}
	

	void mainLoop() {
		while( loop ) {
			printMenu();
			int choice = readMenuChoice();
			runAction( choice );
		}
	}


	public static void main( String[] args ) throws InterruptedException {
		new TheClient();
	}


private static String getFileChecksum(MessageDigest digest, File file) throws IOException
{
    //Get file input stream for reading the file content
    FileInputStream fis = new FileInputStream(file);
     
    //Create byte array to read data in chunks
    byte[] byteArray = new byte[1024];
    int bytesCount = 0; 
      
    //Read file data and update in message digest
    while ((bytesCount = fis.read(byteArray)) != -1) {
        digest.update(byteArray, 0, bytesCount);
    };
     
    //close the stream; We don't need it now.
    fis.close();
     
    //Get the hash's bytes
    byte[] bytes = digest.digest();
     
    //This bytes[] has bytes in decimal format;
    //Convert it to hexadecimal format
    StringBuilder sb = new StringBuilder();
    for(int i=0; i< bytes.length ;i++)
    {
        sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
    }
     
    //return complete hash
   return sb.toString();
}


private static boolean isNumeric(String str) {

	// null or empty
	if (str == null || str.length() == 0) {
		return false;
	}

	for (char c : str.toCharArray()) {
		if (!Character.isDigit(c)) {
			return false;
		}
	}

	return true;

}

}
