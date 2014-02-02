package fw.connection.socks;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

import fw.connection.crypt.GameCryptInterlude;
import fw.connection.game.CLIENT_STATE;
import fw.util.Printer;
import xmlex.jsc.PyroProtocolFeeder;
import xmlex.jsc.protocols.ProtocolL2;
import jawnae.pyronet.PyroClient;
import jext.util.Util;

public class ListenerIntelude extends ISocksListener {

	private static final Logger _log = Logger.getLogger(ListenerIntelude.class
			.getName());

	public GameCryptInterlude _cryptClient,_cryptServer;
	private PacketHandlerInterlude _phandler = new PacketHandlerInterlude();

	private CLIENT_STATE _state = CLIENT_STATE.CONNECTED;
	private boolean _game = false, _login = false;
	private boolean _init = false;

	public ListenerIntelude() {
		// _log.info("Create");
	}

	public void connectedClient(PyroClient client) {
	}

	public void disconnectedClient(PyroClient arg0) {
	}

	public void droppedClient(PyroClient arg0, IOException arg1) {
	}

	public void receivedData(PyroClient client, ByteBuffer buf) {
		if (client == getClient()) {
			_log.info(" receivedData from client");
			getServer().writeCopy(buf);
		}
		if (client == getServer()) {
			_log.info(" receivedData from server");
			getClient().writeCopy(buf);
		}

	}

	public void sentData(PyroClient client, int buf) {
	}

	public void unconnectableClient(PyroClient arg0) {
	}

	private void sendToClientCrypt(ByteBuffer buf){
		_cryptClient.encrypt(buf.array(), 0, buf.array().length);
		sendToClient(buf);
	}
	
	public synchronized void onPacketFromServer(ByteBuffer buf) {
		// _log.info("From Server: "+buf.limit());
		if (!_init) {
			if (buf.limit() == 184)
				_login = true;
			_init = true;
		}
		if (_game) {
			//ByteBuffer _buf = ISocksListener.copy(buf);
			_cryptServer.decrypt(buf.array(), 0, buf.array().length);
			if (_phandler.handlePacketServer(buf, this))				
				sendToClientCrypt(buf);
			else
				_log.info("Not sendS: "+buf.remaining());
		} else {
			sendToClient(buf);
		}

	}

	private void sendToServerCrypt(ByteBuffer buf){
		_cryptServer.encrypt(buf.array(), 0, buf.array().length);
		sendToServer(buf);
	}
	public synchronized void onPacketFromClient(ByteBuffer buf) {
		// _log.info("From Client: "+buf.limit());
		if (!_init) {
			_login = false;
			_game = true;
			_init = true;
			_log.info("Game connect init: " + buf.remaining());
			_cryptServer = new GameCryptInterlude();
			_cryptClient = new GameCryptInterlude();
		}
		if (_game) {
			_cryptClient.decrypt(buf.array(), 0, buf.array().length);
			if (_phandler.handlePacketClient(buf, this))
				sendToServerCrypt(buf);
			else
				_log.info("Not sendC: "+buf.remaining());
		} else
			sendToServer(buf);
	}

	@Override
	public void setClient(PyroClient client) {
		super.setClient(client);
		client.removeListeners();

		PyroProtocolFeeder feeder = new PyroProtocolFeeder(client);
		ProtocolL2 handler = new ProtocolL2() {
			public void onReady(ByteBuffer buf) {
				onPacketFromClient(buf);
			}
		};
		feeder.addByteSink(handler);
		client.addListener(feeder);

		// client.addListener(this);
		// _log.info("Set Client: "+client);
	}

	@Override
	public void setServer(PyroClient server) {
		super.setServer(server);
		server.removeListeners();

		PyroProtocolFeeder feeder = new PyroProtocolFeeder(server);
		ProtocolL2 handler = new ProtocolL2() {
			public void onReady(ByteBuffer buf) {
				onPacketFromServer(buf);
			}
		};
		feeder.addByteSink(handler);
		server.addListener(feeder);
		// _log.info("Set Server: "+server);
	}

	public CLIENT_STATE getState() {
		return _state;
	}

	public void setState(CLIENT_STATE _state) {
		this._state = _state;
	}

	@Override
	public void onDataWrite(ByteBuffer buf) {
		//_log.info(Util.printData(buf.array()));
		_cryptServer.encrypt(buf.array(), 0, buf.array().length);
	}

	@Override
	public void setGameCrypt(byte[] key) {
		_cryptClient.setKey(key);
		_cryptClient._toClient = true;
		_cryptServer.setKey(key);
	}

}