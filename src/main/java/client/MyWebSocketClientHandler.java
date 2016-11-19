package client;

import java.util.concurrent.BlockingQueue;

import util.EnDeCryProcess;

import com.alibaba.fastjson.JSON;

import json.server.login.SuccessLogin;
import json.util.JSONNameandString;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.CharsetUtil;

public class MyWebSocketClientHandler extends SimpleChannelInboundHandler<Object> {


    private final WebSocketClientHandshaker handshaker;
    private ChannelPromise handshakeFuture;
    private ClientAccessHandler accessHandler = null;
    private ClientSession session;
    private BlockingQueue<JSONNameandString> receque;
    private BaseClient client;

    public MyWebSocketClientHandler(WebSocketClientHandshaker handshaker, BlockingQueue<JSONNameandString> receque) {
        this.handshaker = handshaker;
        this.receque = receque;
    }

    public ChannelFuture handshakeFuture() {
        return handshakeFuture;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        session = new ClientSession(ctx.channel());
        handshaker.handshake(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        System.out.println("WebSocket Client disconnected!");
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            handshaker.finishHandshake(ch, (FullHttpResponse) msg);
            System.out.println("WebSocket Client connected!");
            handshakeFuture.setSuccess();
            accessHandler = new ClientAccessHandler();
            accessHandler.handle(null);
            ctx.channel().writeAndFlush(new TextWebSocketFrame(accessHandler.getResult()));
            return;
        }
        
        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            throw new IllegalStateException(
                    "Unexpected FullHttpResponse (getStatus=" + response.status() +
                            ", content=" + response.content().toString(CharsetUtil.UTF_8) + ')');
        }

        WebSocketFrame frame = (WebSocketFrame) msg;
        if (frame instanceof TextWebSocketFrame) {

        		String request = ((TextWebSocketFrame) frame).text();
        		if(!accessHandler.getAccess()){
        			accessHandler.handle(request);
        			ctx.channel().writeAndFlush(new TextWebSocketFrame(accessHandler.getResult()));
        			if(accessHandler.getAccess()){
        				session.setSecretKey(accessHandler.getSecretKey());
        				String login;
						if(session.getUserName()!=null){
        					login =session.login();
        					ctx.channel().writeAndFlush(new TextWebSocketFrame(login));
						}
        			}
        		}else{
        			if(!session.isHasLogin()){
        				JSONNameandString backjson =  session.receiveACK(request);
        				if(session.isHasLogin()){
        					SuccessLogin login = new SuccessLogin();
        					JSONNameandString jsonNameandString = new JSONNameandString();
        					jsonNameandString.setJSONName(SuccessLogin.class.getName());
        					jsonNameandString.setJSONStr(JSON.toJSONString(login));
        					receque.add(jsonNameandString);
        				}else{
        					receque.offer(backjson);
        				}
        			}else{
        				request = EnDeCryProcess.SysKeyDecryWithBase64(request, accessHandler.getSecretKey());
        				JSONNameandString Json = JSON.parseObject(request, JSONNameandString.class);
        				receque.add(Json);
//        				System.out.println(EnDeCryProcess.SysKeyDecryWithBase64(request, accessHandler.getSecretKey()));
        			}
        		}
        } else if (frame instanceof PongWebSocketFrame) {
            System.out.println("WebSocket Client received pong");
        } else if (frame instanceof CloseWebSocketFrame) {
            System.out.println("WebSocket Client received closing");
            ch.close();
        }
    }

	@Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }
        ctx.close();
    }

	public ClientAccessHandler getAccessHandler() {
		return accessHandler;
	}

	public ClientSession getSession() {
		return session;
	}

	public BlockingQueue<JSONNameandString> getReceque() {
		return receque;
	}

	public BaseClient getClient() {
		return client;
	}

	public void setClient(BaseClient client) {
		this.client = client;
	}

}
