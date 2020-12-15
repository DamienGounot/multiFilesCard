package applet;


import javacard.framework.*;
import javacard.security.*;
import javacardx.crypto.*;



public class TheApplet extends Applet {


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

	final static short MAXLENGTH = (short)255;
	static final byte P1_FILENAME 	 	= (byte)0x01;
	static final byte P1_BLOC 	 		= (byte)0x02;
	static final byte P1_VAR 	 		= (byte)0x03;
	static final byte P1_LASTBLOCK 	 		= (byte)0x04;

	static byte[] file = new byte[16384]; // 16Ko

	private final static byte INS_DES_ECB_NOPAD_ENC           	= (byte)0x20;
    private final static byte INS_DES_ECB_NOPAD_DEC           	= (byte)0x21;



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
        cipher.doFinal( buffer, (short)5, Lc, buffer, (short)0);
        /*Renvoi cipher vers Client*/
        apdu.setOutgoingAndSend((short)0, Lc);

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
			case COMPAREFILES: compareFile( apdu ); break;
			case UPDATECARDKEY: updateCardKey( apdu ); break;
			case UNCIPHERFILEBYCARD: uncipherFileByCard( apdu ); break;
			case CIPHERFILEBYCARD: cipherFileByCard( apdu ); break;
			case READFILEFROMCARD: readFileFromCard( apdu ); break;
			case WRITEFILETOCARD: writeFileToCard( apdu ); break;
			case LISTINGFILE: listingFile( apdu ); break;
			default: ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
		}
	}

	void compareFile( APDU apdu ){
		
	}

	void updateCardKey( APDU apdu ) {
	}


	void uncipherFileByCard( APDU apdu ) {
	}


	void cipherFileByCard( APDU apdu ) {
	}


	void readFileFromCard( APDU apdu ) {


		byte[] buffer = apdu.getBuffer();  
		apdu.setIncomingAndReceive();
		
		switch(buffer[2]){
			case P1_FILENAME:
				/* envoi filename */
				Util.arrayCopy(file, (byte)1, buffer, (byte)0, file[0]);
				apdu.setOutgoingAndSend((short)0, file[0]);
				/* end */
			break;
			case P1_BLOC:

					/* envoi d'un bloc */
					short offset = (short)((((byte)1 + file[0] + (byte)2) + (buffer[3] * (short)MAXLENGTH)));
					buffer = apdu.getBuffer();
					Util.arrayCopy(file, offset, buffer, (byte)0, (short)MAXLENGTH);
					apdu.setOutgoingAndSend((short)0, (short)MAXLENGTH);
					/* end */
			break;
			case P1_LASTBLOCK:

					
					/* envoi du dernier bloc */
					byte nbAPDUMax = file[(byte)(file[0]+(byte)1)];
					byte lastAPDUsize = file[(byte)(file[0]+(byte)2)];

					short offset_last = (short)((((byte)1 + (byte)file[0]) + (byte)2) + ((byte)(nbAPDUMax) * (short)MAXLENGTH));
					
					buffer = apdu.getBuffer();
					Util.arrayCopy(file, offset_last, buffer, (byte)0, (short)(lastAPDUsize&(short)255));
					apdu.setOutgoingAndSend((short)0, (short)(lastAPDUsize&(short)255));
					/* end */			
			break;
			case P1_VAR:
				/* envoi parametre nbAPDUMax et lastAPDUsize */
				Util.arrayCopy(file, (byte)(file[0]+(byte)1), buffer, (byte)0, (byte)2);
				apdu.setOutgoingAndSend((short)0, (byte)2);
				/* end */
			break;
			default:
		}
	}


	void writeFileToCard( APDU apdu ) {
		byte[] buffer = apdu.getBuffer();  
		apdu.setIncomingAndReceive();
		
		switch(buffer[2]){
			case P1_FILENAME:
			Util.arrayCopy(buffer, (byte)4, file, (byte)0, (byte)(buffer[4]+(byte)1));
			break;
			case P1_BLOC:
			short offset = (short)((((byte)1 + file[0] + (byte)2) + (buffer[3] * (short)MAXLENGTH)));

			Util.arrayCopy(buffer, (byte)5, file, offset, (short)(buffer[4]&(short)255));
			break;
			case P1_VAR:
			Util.arrayCopy(buffer, (byte)5, file, (byte)((byte)1 + file[0]),(short)(buffer[4]&(short)255));
			break;
			default:
		}
	}

	void listingFile( APDU apdu ){

	}

}
