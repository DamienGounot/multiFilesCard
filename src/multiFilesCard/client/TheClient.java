package client;

import java.util.Date;
import java.io.*;
import opencard.core.service.*;
import opencard.core.terminal.*;
import opencard.core.util.*;
import opencard.opt.util.*;


public class TheClient {

	private PassThruCardService servClient = null;
	boolean DISPLAY = true;
	boolean loop = true;

	static final byte CLA					= (byte)0x90;
	static final byte P1					= (byte)0x00;
	static final byte P2					= (byte)0x00;

	static final byte COMPAREFILES 				= (byte)0x07;
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
	static final byte P1_LASTBLOCK 	 		= (byte)0x04;
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
		
	}


	void updateCardKey() {
		int dataSize =0;
		String DESKey = "";
		do{
		System.out.println("Saisissez la clef DES (8 bytes):");
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
					//System.out.println("return :"+return_value);

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
						//System.out.println("Padding was: "+padding_extrait);
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
					//System.out.println("return :"+return_value);

					if(return_value == CIPHER_MAXLENGTH){
						response = cipherGeneric(CIPHERFILEBYCARD,INS_DES_ECB_NOPAD_ENC, cipherdataBlock);
						
					}else{

						 int paddingSize = (8-(return_value%8));
						 //System.out.println("PAdding: "+paddingSize);
						 byte[] finalData = new byte[return_value+paddingSize];
						 //System.out.println("Allocation with padding included: "+(return_value+paddingSize));
						 
						 byte[] finalPadding = new byte[paddingSize];
						 for(int i =0; i < paddingSize ; i++){
						 finalPadding[i]= (byte)(paddingSize+48); //(+48 pour offset dans la table ASCII)
						 }

						 System.arraycopy(cipherdataBlock, (byte)0, finalData, (byte)0, return_value);
						 System.arraycopy(finalPadding, (byte)0, finalData,return_value,paddingSize);

						 response = cipherGeneric(CIPHERFILEBYCARD,INS_DES_ECB_NOPAD_ENC, finalData);
					}
					//System.out.println("Block !");
					//System.out.println("Response length: "+response.length);
					cipherdata.write(response);

				}

			}catch(Exception e){
				System.out.println(e);
			}		
	}

	void readFileFromCard() {

		/* Read filename */
		System.out.println("==========Requete: Filename==========");
		byte[] header = {CLA,READFILEFROMCARD, P1_FILENAME,P2}; 
		byte[] optional = {0x00};
		byte[] command = new byte[(byte)header.length + (byte)optional.length];
		System.arraycopy(header,(byte)0,command,(byte)0,(byte)header.length);
		System.arraycopy(optional,(byte)0,command,(byte)header.length,(byte)optional.length);
		CommandAPDU cmd = new CommandAPDU( command);
		ResponseAPDU resp = this.sendAPDU( cmd, DISPLAY );
		System.out.println("==========Fin Requete: Filename==========");
		/* end */

		byte[] bytes = resp.getBytes();
		String filename = "";
	    for(int i=0; i<bytes.length-2;i++)
		filename += new StringBuffer("").append((char)bytes[i]);



		/* Read  Valeurs Variables nbAPDUMax et lastAPDUsize */
		System.out.println("==========Requete: Valeurs Variables==========");
		byte[] header1 = {CLA,READFILEFROMCARD, P1_VAR,P2}; 
		byte[] optional1 = {0x00};
		byte[] command1 = new byte[(byte)header1.length + (byte)optional1.length];
		System.arraycopy(header1,(byte)0,command1,(byte)0,(byte)header1.length);
		System.arraycopy(optional1,(byte)0,command1,(byte)header1.length,(byte)optional1.length);
		CommandAPDU cmd1 = new CommandAPDU( command1);
		ResponseAPDU resp1 = this.sendAPDU( cmd1, DISPLAY );
		System.out.println("==========Fin Requete: Valeurs Variables==========");
		/* end */

		bytes = resp1.getBytes();
		int nbAPDUMax = bytes[0];
		int lastAPDUsize = bytes[1];
		System.out.println("RECEPTION: nbAPDUMAx: "+nbAPDUMax+"; lastAPDUsize: "+lastAPDUsize);




		try{
			DataOutputStream filedata = new DataOutputStream(new FileOutputStream("retour_"+filename));			


			for(int indice = 0 ; indice < nbAPDUMax; indice++)
			{

				/* Requete bloc d'indice P2*/
				System.out.println("==========Requete: Bloc==========");
				byte[] command2 = {CLA,READFILEFROMCARD, P1_BLOC,(byte)indice,0x00}; 
				CommandAPDU cmd2 = new CommandAPDU( command2);
				ResponseAPDU resp2 = this.sendAPDU( cmd2, DISPLAY );
				System.out.println("==========Fin Requete: Bloc==========");
				/* end */

				//short offset = (short)((((byte)1 + (byte)8) + (byte)2) + ((byte)(indice) * (byte)MAXLENGTH));
				//System.out.println("L'Offset coté applet etait de: "+offset);

				byte[] block = resp2.getBytes();
				
				// A mettre dans le fichier
				filedata.write(block, 0, (block.length-2));
			}

				/* Requete dernier bloc*/
				System.out.println("==========Requete: Last Bloc==========");
				byte[] command2 = {CLA,READFILEFROMCARD, P1_LASTBLOCK,P2,0x00}; 
				CommandAPDU cmd2 = new CommandAPDU( command2);
				ResponseAPDU resp2 = this.sendAPDU( cmd2, DISPLAY );
				System.out.println("==========Fin Requete: Last Bloc==========");
				/* end */		
				byte[] block = resp2.getBytes();
				
				// A mettre dans le fichier
				filedata.write(block,0, (block.length-2));

		}catch(Exception e){
			System.out.println(e);
		}
	}


	void writeFileToCard() {
		System.out.println("Saisissez le fichier a ecrire sur la carte:");
		String filename = readKeyboard();
		byte filenameSize = (byte)filename.getBytes().length;
		int nbAPDUMax = 0;
		int lastAPDUsize = 0;

		/* envoi size filename et filename */
		System.out.println("==========Requete: Filename==========");
		byte[] header = {CLA,WRITEFILETOCARD, P1_FILENAME,P2}; // requete de type "filename" ( contient la taille de filename et filename)
		byte[] optional = new byte[(byte)1 + filenameSize];
		byte[] command = new byte[header.length + optional.length];
		optional[0] = filenameSize;
		System.arraycopy(filename.getBytes(), (byte)0, optional, (byte)1, filenameSize);
		System.arraycopy(header,(byte)0,command,(byte)0,header.length);
		System.arraycopy(optional,(byte)0,command,header.length,optional.length);
		CommandAPDU cmd = new CommandAPDU( command);
		ResponseAPDU resp = this.sendAPDU( cmd, DISPLAY );
		System.out.println("==========Fin Requete: Filename==========");
		/* end */

	
		try{
			DataInputStream filedata = new DataInputStream(new FileInputStream(filename));
		
		int return_value = 0;

		while( (return_value = filedata.read(dataBlock,0,MAXLENGTH)) !=-1 ) {
				//System.out.println("return :"+return_value);
			if(return_value == MAXLENGTH){
				nbAPDUMax ++;

				//System.out.println("Indice du bloc :"+(nbAPDUMax-1));

				/* envoi d'un bloc */
				System.out.println("==========Requete: Bloc==========");
				byte[] header1 = {CLA,WRITEFILETOCARD,P1_BLOC,(byte)(nbAPDUMax-1)}; // requete de type "bloc" (contient un bloc de 126 octets) avec P2 = indice du bloc
				
				//System.out.println("Memoire allouee pour optional: "+(return_value+1));

				byte[] optional1 = new byte[(return_value+1)];

				//System.out.println("Memoire allouee pour command: "+(header1.length + optional1.length));

				byte[] command1 = new byte[header1.length +optional1.length];

				optional1[0] =  (byte)return_value;

				//System.out.println("Lc: "+return_value);

				System.arraycopy(dataBlock, (byte)0, optional1, (byte)1, return_value);

				//System.out.println("header1.length: "+header1.length);
				System.arraycopy(header1,(byte)0,command1,(byte)0,header1.length);

				//System.out.println("optional1.length: "+optional1.length);
				System.arraycopy(optional1,(byte)0,command1,header1.length,optional1.length);


				CommandAPDU cmd1 = new CommandAPDU( command1);
				ResponseAPDU resp1 = this.sendAPDU( cmd1, DISPLAY );
				System.out.println("==========Fin Requete: Bloc==========");
				/* end */


			}else{

				lastAPDUsize = return_value;

				//System.out.println("Indice du bloc :"+(nbAPDUMax));




				/* envoi du DERNIER bloc */
				System.out.println("==========Requete: Bloc==========");
				byte[] header2 = {CLA,WRITEFILETOCARD,P1_BLOC,(byte)nbAPDUMax}; // requete de type "bloc" (contient un bloc de lastAPDUsize octets) avec P2 = indice du bloc
				
				byte[] optional2 = new byte[(byte)1 + lastAPDUsize];

				byte[] command2 = new byte[header2.length + optional2.length];

				optional2[0] = (byte)lastAPDUsize;

				System.arraycopy(dataBlock, (byte)0, optional2, (byte)1, lastAPDUsize);
				System.arraycopy(header2,(byte)0,command2,(byte)0,header2.length);
				System.arraycopy(optional2,(byte)0,command2,header2.length,optional2.length);

				CommandAPDU cmd2 = new CommandAPDU( command2);
				ResponseAPDU resp2 = this.sendAPDU( cmd2, DISPLAY );
				System.out.println("==========Fin Requete: Bloc==========");
				/* end */

				
				System.out.println("nbAPDUMax :"+nbAPDUMax+"; lastAPDUsize :"+lastAPDUsize+"; Total length: "+(nbAPDUMax*MAXLENGTH+lastAPDUsize)+"bytes");


				/* envoi des valeurs */
				System.out.println("==========Requete: Valeurs Variables==========");
				byte[] header3 = {CLA,WRITEFILETOCARD,P1_VAR,P2}; // requete de type "var" (contient nbAPDUMax et lastAPDUsize)
				byte[] optional3 = {(byte)0x02,(byte)nbAPDUMax,(byte)lastAPDUsize};

				byte[] command3 = new byte[header3.length +optional3.length];
				System.arraycopy(header3,(byte)0,command3,(byte)0,header3.length);
				System.arraycopy(optional3,(byte)0,command3,header3.length,optional3.length);

				CommandAPDU cmd3 = new CommandAPDU( command3);
				ResponseAPDU resp3 = this.sendAPDU( cmd3, DISPLAY );
				/* end */
				System.out.println("==========Fin Requete: Valeurs Variables==========");

			}

		}

		}catch(Exception e){
			System.out.println(e);
		}
	}

	void listingFile(){

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
		System.out.println( "7: Compare two files (saisie clavier des noms des deux fichiers) (NB: process fait uniquement cote Client)" );
		System.out.println( "6: Changer DES key (saisie clavier de la clef)" );
		System.out.println( "5: Uncipher file using DES (saisie clavier des noms des fichiers d entree et de sortie)" );
		System.out.println( "4: Cipher file using DES (saisie clavier des noms des fichiers d entree et de sortie)" );
		System.out.println( "3: Read file from card (saisie clavier du numero)" );
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

		//System.out.println("[CIPHER]Lc:" +((short)optional[0]&(short)255));

		System.arraycopy(challenge, 0, optional, (byte)1, ((short)optional[0]&(short)255));
		byte[] command = new byte[header.length + optional.length];
		System.arraycopy(header, (byte)0, command, (byte)0, header.length);
		System.arraycopy(optional, (byte)0, command,header.length, optional.length);
		CommandAPDU cmd = new CommandAPDU( command);
		//displayAPDU(cmd);

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


}
