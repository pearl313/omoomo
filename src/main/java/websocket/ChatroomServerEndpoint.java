package websocket;

import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonWriter;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.json.JSONObject;

import socket.Board;
import socket.SocketConnection;

@ServerEndpoint(value="/chatroomServerEndpoint", configurator=ChatroomServerconfigurator.class)
public class ChatroomServerEndpoint {
	// 방과 유저 정보
	static SocketConnection sc = new SocketConnection();
	// 게임 판 정보(Map<방번호, 게임판>)
	static Map<String, Board> boards = new HashMap<>();
	
	@OnOpen
	public void handleOpen(EndpointConfig endpointConfig, Session userSession) {
		// 세션에 이름과 방 번호 저장
		userSession.getUserProperties().put("username", endpointConfig.getUserProperties().get("username"));
		userSession.getUserProperties().put("roomNumber", endpointConfig.getUserProperties().get("roomNumber"));
		
		String roomNumber = (String) endpointConfig.getUserProperties().get("roomNumber");
		// 세션에 해당 번호의 방이 있는지 확인해서, 있으면 유저만 추가, 없으면 방 추가 후 유저 추가
		sc.enterRoom(roomNumber, (Session) userSession);
		boards.put(roomNumber, new Board());
		// 현재 소켓 접속자 현황 확인용 로그
		System.out.println("[서버] 유저 입장");
		System.out.println("===== 현재 방 현황 =====");
		System.out.println("| 방 번호 | 현재 유저 수 |");
		for(String room : sc.getUserSockets().keySet()) {
		    System.out.println("---------------------");
		    System.out.println("| " + room + " | " + sc.getUserSockets().get(room).size() + "명 |");
		}
		System.out.println("---------------------");
//		JSONObject json = new JSONObject();
//		json.put("c", (String) userSession.getUserProperties().get("c"));
		
//        try{
//            userSession.getBasicRemote().sendText(json.toString());
//        } catch(Exception e) {
//            e.printStackTrace();
//        }
	}
	
	@OnMessage
	public void handleMessage(String message, Session userSession) throws Exception {
		System.out.println("[서버] 메세지 받음");
		String username = (String) userSession.getUserProperties().get("username");
		String roomNumber = (String) userSession.getUserProperties().get("roomNumber");
		if(username != null) {
			JSONObject reqMessage = new JSONObject(message);
			System.out.println("요청 유형: " + reqMessage.get("sign"));
			if("init".equals(reqMessage.get("sign"))) {
			    JSONObject resMessage = new JSONObject();
			    resMessage.put("sign", "init");
                resMessage.put("c", userSession.getUserProperties().get("c"));
                try{
                    userSession.getBasicRemote().sendText(resMessage.toString());
                } catch(Exception e) {
                    e.printStackTrace();
                }
			} else if("chat".equals(reqMessage.get("sign"))) {
				// 받은 메시지가 채팅 메시지 인 경우 - 채팅 메시지를 같은 방내 다른 사용자에게 전송
				sc.sendMessage(roomNumber, username, (String) reqMessage.get("m"));
			} else if("stone".equals(reqMessage.get("sign"))){
                int h = reqMessage.getInt("h");
                int v = reqMessage.getInt("v");
                if(boards.get(roomNumber).checkOne(h, v)) {
			        boards.get(roomNumber).setStone(h, v, (int) userSession.getUserProperties().get("c"));
			    }
			    
				// 받은 메시지가 돌을 놓은 좌표인 경우 - 놓은 좌포를 같은 방내 다른 사용자에게 전송
				
				// 전체(or 놓은 위치 주변) 오목판 정보를 받아 오목 판별
				
				/******** 여기에 오목 판별 로직 추가 ********/
			    // stone color, stone location, stone area
			    // stone xy: {x, y} = {horizon, vertical}
			    /* 
			     *  List<ArrayList<HashMap<String, Integer> stone area = 
			        {
			            {{h: 0, v: 4}, {h: 0, v: 4}, {h: 0, v: 4}, ...},
                        {{h: 0, v: 4}, {h: 0, v: 4}, {h: 0, v: 4}, ...},
			            ...
		            }
		            HashMap<String, Integer> stone location = {h: 0, v: 4}
		            int stone_color =  -1 ? 1 // black = -1 , white = 1
			     */
			    // logic.win((Map) stone_color, (Map) stone_location, (List<ArrayList<HashMap<String, Integer>>) stone_area);
				
			    /*
			     1. stone 5 ?
			     2. Need stone 33 ?
			         2-1. Yes! => check 33
			         2-2. No! => pass
			     3. stone 44 ?
			         3-1. Yes! => check 44
			         3-2. No! => pass
			     */

				// 판별 결과와 놓은 위치 값을 같은 방내 다른 사람에게 전송
				JSONObject resMessage = new JSONObject();
				resMessage.put("sign", "game");
                resMessage.put("h", reqMessage.get("h"));
                resMessage.put("v", reqMessage.get("v"));
                resMessage.put("c", userSession.getUserProperties().get("c"));
				sc.getUserSockets().get((String) roomNumber).stream().forEach(x -> {
					try{
						x.getBasicRemote().sendText(resMessage.toString());
					} catch(Exception e) {
						e.printStackTrace();
					}
				});
			}
		}
	}
	
	@OnClose
	public void handleClose(Session userSession) {
		// 방에서 나가는 경우, 해당 인원의 세션 제거(유저가 아무도 없게 되면 방도 삭제)
	    sc.exitRoom(userSession);
		
		// 현재 소켓 접속자 현황 확인용 로그
		System.out.println("[서버] 유저 나감");
		System.out.println("===== 현재 방 현황 =====");
		System.out.println("| 방 번호 | 현재 유저 수 |");
		for(String room : sc.getUserSockets().keySet()) {
            System.out.println("---------------------");
            System.out.println("| " + room + "번방 | " + sc.getUserSockets().get(room).size() + "명 |");
        }
		System.out.println("---------------------");
	}
	
	@OnError
	public void handleError(Throwable t) {}
	
//	private String buildJsonData(String username, String message) {
//		// 채팅 메세지 생성 메서드
//		System.out.println("[서버] 메세지 생성");
//		JsonObject jsonObject = Json.createObjectBuilder().add("message", username + ": " + message).build();
//		StringWriter stringWriter = new StringWriter();
//		try (JsonWriter jsonWriter = Json.createWriter(stringWriter)) {jsonWriter.write(jsonObject);}
//		return stringWriter.toString();
//	}
}