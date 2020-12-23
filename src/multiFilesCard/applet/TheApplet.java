package applet;

import javacard.framework.*;
import javacard.security.*;
import javacardx.crypto.*;



public class TheApplet extends Applet {


	static final byte CLA					= (byte)0x90;
	static final byte P1					= (byte)0x00;
	static final byte P2					= (byte)0x00;

	static final byte UPDATECARDKEY				= (byte)0x06;
	static final byte UNCIPHERFILEBYCARD		= (byte)0x05;
	static final byte CIPHERFILEBYCARD			= (byte)0x04;
	static final byte READFILEFROMCARD			= (byte)0x03;
	static final byte WRITEFILETOCARD			= (byte)0x02;
	static final byte LISTINGFILE			= (byte)0x01;

	final static short MAXLENGTH = (short)255;
	static final byte P1_FILENAME 	 	= (byte)0x01;
	static final byte P1_BLOC 	 		= (byte)0x02;
	static final byte P1_VAR 	 		= (byte)0x03;
	static final byte P1_LASTBLOCK 		= (byte)0x04;
	static final byte P1_NBFILES		= (byte)0x05;
	static final byte P1_FILEINFO		= (byte)0x06;
	static final byte P1_OFFSET 	 	= (byte)0x07;
	
	static byte[] file = new byte[16384]; // 16Ko
	private final static byte INS_DES_ECB_NOPAD_ENC           	= (byte)0x20;
	private final static byte INS_DES_ECB_NOPAD_DEC           	= (byte)0x21;
	
	public static short OFFSET_VALUE = 0;



	static final byte[] theDESKey = 
		new byte[] { (byte)0xCA, (byte)0xCA, (byte)0xCA, (byte)0xCA, (byte)0xCA, (byte)0xCA, (byte)0xCA, (byte)0xCA };
    // cipher instances
    private Cipher 
	    cDES_ECB_NOPAD_enc, cDES_ECB_NOPAD_dec;
    // key objects
    private Key 
	    secretDESKey, secretDES2Key, secretDES3Key;
    boolean 
	    pseudoRandom, secureRandom,
	    SHA1, MD5, RIPEMD160,
	    keyDES, DES_ECB_NOPAD, DES_CBC_NOPAD;




	protected TheApplet() {
	    initKeyDES(); 
	    initDES_ECB_NOPAD(); 
		
		this.register();
	}

    private void initKeyDES() {
	    try {
		    secretDESKey = KeyBuilder.buildKey(KeyBuilder.TYPE_DES, KeyBuilder.LENGTH_DES, false);
		    ((DESKey)secretDESKey).setKey(theDESKey,(short)0);
		    keyDES = true;
	    } catch( Exception e ) {
		    keyDES = false;
	    }
    }


    private void initDES_ECB_NOPAD() {
	    if( keyDES ) try {
		    cDES_ECB_NOPAD_enc = Cipher.getInstance(Cipher.ALG_DES_ECB_NOPAD, false);
		    cDES_ECB_NOPAD_dec = Cipher.getInstance(Cipher.ALG_DES_ECB_NOPAD, false);
		    cDES_ECB_NOPAD_enc.init( secretDESKey, Cipher.MODE_ENCRYPT );
		    cDES_ECB_NOPAD_dec.init( secretDESKey, Cipher.MODE_DECRYPT );
		    DES_ECB_NOPAD = true;
	    } catch( Exception e ) {
		    DES_ECB_NOPAD = false;
	    }
    }

	private void cipherGeneric( APDU apdu, Cipher cipher) {
        byte[] buffer = apdu.getBuffer();
        
        /*Reception de la commande Client*/
        apdu.setIncomingAndReceive();
        byte Lc = buffer[4];
        /*Cipher*/
        cipher.doFinal( buffer, (short)5, (short)((short)Lc&(short)255), buffer, (short)0);
        /*Renvoi cipher vers Client*/
        apdu.setOutgoingAndSend((short)0, (short)((short)Lc&(short)255));

	}


	public static void install(byte[] bArray, short bOffset, byte bLength) throws ISOException {
		new TheApplet();
	} 


	public boolean select() {
			return true;
	} 


	public void deselect() {

	}

	public void process(APDU apdu) throws ISOException {
		if( selectingApplet() == true )
			return;

		byte[] buffer = apdu.getBuffer();

		switch( buffer[1] ) 	{
			case UPDATECARDKEY: updateCardKey( apdu ); break;
			case UNCIPHERFILEBYCARD: uncipherFileByCard( apdu ); break;
			case CIPHERFILEBYCARD: cipherFileByCard( apdu ); break;
			case READFILEFROMCARD: readFileFromCard( apdu ); break;
			case WRITEFILETOCARD: writeFileToCard( apdu ); break;
			case LISTINGFILE: listingFile( apdu ); break;
			default: ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
		}
	}

	void updateCardKey( APDU apdu ) {
		byte[] buffer = apdu.getBuffer();  
		apdu.setIncomingAndReceive();
		Util.arrayCopy(buffer, (byte)5, theDESKey, (byte)0,buffer[4]);
		initKeyDES(); 
	    initDES_ECB_NOPAD(); 
		
		/* Just to proove that DES key was succesfully updated ! */
		Util.arrayCopy(theDESKey, (byte)(0), buffer, (byte)0, (byte)8);
		apdu.setOutgoingAndSend((short)0, (short)8);		
	}


	void uncipherFileByCard( APDU apdu ) {
		byte[] buffer = apdu.getBuffer();
		cipherGeneric( apdu, cDES_ECB_NOPAD_dec);		
	}


	void cipherFileByCard( APDU apdu ) {
		byte[] buffer = apdu.getBuffer();
		cipherGeneric( apdu, cDES_ECB_NOPAD_enc);
	}


	void readFileFromCard( APDU apdu ) {
		byte[] buffer = apdu.getBuffer();  
		apdu.setIncomingAndReceive();
		
		switch(buffer[2]){

			case P1_OFFSET:
			OFFSET_VALUE = 0;
			byte filenameSize = 0;
			byte nbAPDU = 0;
			short lastAPDUsize = 0;

			for (byte i = 0; i < buffer[3]; i++) {

				filenameSize = file[(short)(OFFSET_VALUE+(short)1)];
				nbAPDU = file[(short)(OFFSET_VALUE + (short)filenameSize + (short)2)];
				lastAPDUsize = file[(short)(OFFSET_VALUE + (short)filenameSize + (short)3)];

				OFFSET_VALUE = (short)(OFFSET_VALUE + (short)((short)filenameSize + (short)3 + (short)((short)nbAPDU * (short)MAXLENGTH) + (short)(lastAPDUsize&(short)255))); //GOOD
			}

			break;
			case P1_FILENAME:
				/* envoi filename */
				Util.arrayCopy(file, (short)(OFFSET_VALUE+(short)2), buffer, (byte)0, file[(short)(OFFSET_VALUE+(short)1)]);
				apdu.setOutgoingAndSend((short)0, file[(short)(OFFSET_VALUE+(short)1)]);
				/* end */
			break;
			case P1_BLOC:

					/* envoi d'un bloc */
					//short offset = (short)((((byte)1 + file[0] + (byte)2) + (buffer[3] * (short)MAXLENGTH)));
					short offset = (short)(OFFSET_VALUE+(short)1+(short)file[(short)(OFFSET_VALUE+(short)1)]+(short)3+(short)(buffer[3] * (short)MAXLENGTH));
					buffer = apdu.getBuffer();
					Util.arrayCopy(file, offset, buffer, (byte)0, (short)MAXLENGTH);
					apdu.setOutgoingAndSend((short)0, (short)MAXLENGTH);
					/* end */
			break;
			case P1_LASTBLOCK:

					
					/* envoi du dernier bloc */
					//byte nbAPDUMax = file[(byte)(file[0]+(byte)1)];
					//byte lastAPDU = file[(byte)(file[0]+(byte)2)];
					byte nbAPDUMAx = file[(short)(OFFSET_VALUE+(short)1+(short)file[(short)(OFFSET_VALUE+(short)1)]+(short)1)];
					byte lastAPDU = file[(short)(OFFSET_VALUE+(short)1+(short)file[(short)(OFFSET_VALUE+(short)1)]+(short)2)];
					//short offset_last = (short)((((byte)1 + (byte)file[0]) + (byte)2) + ((byte)(nbAPDUMax) * (short)MAXLENGTH));
					short offset_last = (short)(OFFSET_VALUE+(short)1+(short)file[(short)(OFFSET_VALUE+(short)1)]+(short)3+(short)(nbAPDUMAx * (short)MAXLENGTH));
					buffer = apdu.getBuffer();
					Util.arrayCopy(file, offset_last, buffer, (byte)0, (short)(lastAPDU&(short)255));
					apdu.setOutgoingAndSend((short)0, (short)(lastAPDU&(short)255));
					/* end */			
			break;
			case P1_VAR:
				/* envoi parametre nbAPDUMax et lastAPDUsize */
				Util.arrayCopy(file, (short)(OFFSET_VALUE+(short)2+(short)file[(short)(OFFSET_VALUE+(short)1)]), buffer, (byte)0, (byte)2);
				apdu.setOutgoingAndSend((short)0, (byte)2);
				/* end */
			break;
			default:
		}
	}


	void writeFileToCard( APDU apdu ) {
		byte[] buffer = apdu.getBuffer();  
		apdu.setIncomingAndReceive();


		if(file[0]==0x00) // si ecriture du premier fichier
		{
			switch(buffer[2]){
				case P1_FILENAME:
				Util.arrayCopy(buffer, (byte)4, file, (byte)1, (byte)(buffer[4]+(byte)1));
				break;
				case P1_BLOC:
				short offset = (short)((((byte)2 + file[1] + (byte)2) + (buffer[3] * (short)MAXLENGTH)));
	
				Util.arrayCopy(buffer, (byte)5, file, offset, (short)(buffer[4]&(short)255));
				break;
				case P1_VAR:
				Util.arrayCopy(buffer, (byte)5, file, (byte)((byte)2 + file[1]),(short)(buffer[4]&(short)255));
				file[0] = (byte) (file[0] + (byte)1);
				break;
				default:
			}
		}
		else		// si ecriture fichier quelconque ----> A CHECKER
		{
			byte numFichier = file[0];
			short OFFSET = 0;
			byte filenameSize = 0;
			byte nbAPDU = 0;
			short lastAPDUsize = 0;

			for (byte i = 0; i < numFichier; i++) {

				filenameSize = file[(short)(OFFSET+(short)1)];
				nbAPDU = file[(short)(OFFSET + (short)filenameSize + (short)2)];
				lastAPDUsize = file[(short)(OFFSET + (short)filenameSize + (short)3)];

				OFFSET = (short)(OFFSET + (short)((short)filenameSize + (short)3 + (short)((short)nbAPDU * (short)MAXLENGTH) + (short)(lastAPDUsize&(short)255))); //GOOD
			}
	
			
			switch(buffer[2]){
				case P1_FILENAME:
				Util.arrayCopy(buffer, (byte)4, file, (short)(OFFSET+1), (byte)(buffer[4]+(byte)1));
				break;
				case P1_BLOC:

				short offset_bloc = (short)((OFFSET +(short)((short)1 + file[(short)(OFFSET+(short)1)] + (short)3) + (buffer[3] * (short)MAXLENGTH)));
	
				Util.arrayCopy(buffer, (byte)5, file, offset_bloc, (short)(buffer[4]&(short)255));
				break;
				case P1_VAR:
				Util.arrayCopy(buffer, (byte)5, file, (short)(OFFSET + file[(short)(OFFSET+(short)1)]+(short)2),(short)(buffer[4]&(short)255));
				file[0] = (byte) (file[0] + 1);
				break;
				default:
			}
		}

	}

	void listingFile( APDU apdu ){
		byte[] buffer = apdu.getBuffer();

		switch (buffer[2]) {

				case P1_NBFILES:
					Util.arrayCopy(file, (byte)(0), buffer, (byte)0, (byte)1);
					apdu.setOutgoingAndSend((short)0, (short)1);						
				break;

				case P1_FILEINFO:

					byte indiceFichier = buffer[3];


					if(indiceFichier == 0x00) // ----> si demande du premier fichier
					{
						byte filenameSize = file[1];
						byte nbAPDU = file[(short)(1+filenameSize+1)];
						short lastAPDUsize = file[(short)(1+filenameSize+2)];
						buffer[0] = (byte) (indiceFichier); // indiceFichier
						buffer[1] = nbAPDU;	//nbAPDU
						buffer[2] = (byte)lastAPDUsize; //lastAPDUsize
						buffer[3] = filenameSize; //filenameSize
						Util.arrayCopy(file,(short)2, buffer, (byte)4, buffer[3]); //filename
						apdu.setOutgoingAndSend((short)0, (short)((short)4+buffer[3]));

					}
					else		// ----> si demande de fichier quelconque A CHECKER
					{
						short OFFSET = 0;
						byte filenameSize = 0;
						byte nbAPDU = 0;
						short lastAPDUsize = 0;
	
						for (byte i = 0; i < indiceFichier; i++) {
	
							filenameSize = file[(short)(OFFSET+(short)1)];
							nbAPDU = file[(short)(OFFSET + (short)filenameSize + (short)2)];
							lastAPDUsize = file[(short)(OFFSET + (short)filenameSize + (short)3)];
	
							OFFSET = (short)(OFFSET + (short)filenameSize + (short)3 + (short)((short)nbAPDU * (short)MAXLENGTH) + (short)((short)lastAPDUsize&(short)255)); //GOOD
						}
						
						filenameSize = file[(short)(OFFSET+(short)1)];
						nbAPDU = file[(short)(OFFSET + (short)filenameSize + (short)2)];
						lastAPDUsize = file[(short)(OFFSET + (short)filenameSize + (short)3)];
						
							/* NB: ERREUR ICI JE REMPLISSAIS AVEC LES INFOS QUI M'ON PERMI DE ME DECALLER , PAS LES INFOS OFFSETTEES EN ELLES MEME !!!*/
						
						buffer[0] = indiceFichier; //indiceFichier 
						buffer[1] = nbAPDU;	//nbAPDU
						buffer[2] = (byte) lastAPDUsize; // lastAPDUsize
						buffer[3] = filenameSize; //filenameSize
						

						Util.arrayCopy(file, (short)(OFFSET+(short)2), buffer, (byte)4, buffer[3]); //filename
						apdu.setOutgoingAndSend((short)0, (short)((short)4+buffer[3]));
					}

					
					
				break;		
			default:
				break;
		}

	}

}
