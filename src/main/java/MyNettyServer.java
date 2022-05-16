import cz.cvut.fel.esw.server.proto.Request;
import cz.cvut.fel.esw.server.proto.Response;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

public class MyNettyServer {
    private final Set<String> uniqueWords = ConcurrentHashMap.newKeySet();
    private final int streamSize = 1_000_000;


    public static void main(String[] args) throws Exception {
        new MyNettyServer().run();
    }

    public void run() throws InterruptedException {
        EventLoopGroup threadGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(threadGroup)
                    .localAddress(new InetSocketAddress(8080))
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ServerInitializer());

            ChannelFuture channelFuture = serverBootstrap.bind().sync();

            channelFuture.channel().closeFuture().sync();
        }
        finally {
            threadGroup.shutdownGracefully();
        }
    }

    public class ServerInitializer extends ChannelInitializer<SocketChannel> {

        @Override
        protected void initChannel(SocketChannel socketChannel) {
            ChannelPipeline pipeline = socketChannel.pipeline();
            pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(streamSize, 0, 4, 0, 4));
            pipeline.addLast("frameEncoder", new LengthFieldPrepender(4));
            pipeline.addLast("protobufDecoder", new ProtobufDecoder(Request.getDefaultInstance()));
            pipeline.addLast("protobufEncoder", new ProtobufEncoder());
            pipeline.addLast("protobufMessageResponseHandler", new ResponseHandler());
        }
    }

    public class ResponseHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object rawMessage) throws Exception {
            Request request = (Request) rawMessage;
            Response response = handleProtobuf(request);
            ctx.writeAndFlush(response);
        }
    }
    
    private Response handleProtobuf(Request request) throws IOException {
        Response response;
        if (request.hasGetCount()) {
            response = receiveGetCountRequest();
        } else if (request.hasPostWords()) {
            response = receivePostWords(request);
        } else {
            response = receiveInvalidRequest();
        }
        return response;
    }

    private Response fillUniqueWords(BufferedReader bufferedReader) throws IOException {
        Response response;
        String oneLine;
        while((oneLine = bufferedReader.readLine()) != null) {
            StringTokenizer st = new StringTokenizer(oneLine);
            while (st.hasMoreTokens()) {
                uniqueWords.add(st.nextToken());
            }
        }
        response = Response.newBuilder().setStatus(Response.Status.OK).build();
        return response;
    }

    private Response receivePostWords(Request request) throws IOException {
        Response response;
        byte[] postWords = request.getPostWords().getData().toByteArray();
        GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(postWords), streamSize);
        InputStreamReader inputStreamReader = new InputStreamReader(gzipInputStream, StandardCharsets.UTF_8);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        response = fillUniqueWords(bufferedReader);
        return response;
    }

    private Response receiveGetCountRequest(){
        Response response;
        response = Response.newBuilder().setStatus(Response.Status.OK).setCounter(uniqueWords.size()).build();
        uniqueWords.clear();
        return response;
    }
    
    private Response receiveInvalidRequest() {
        Response response;
        response = Response.newBuilder().setStatus(Response.Status.ERROR).setErrMsg("The request is invalid").build();
        return response;
    }
}