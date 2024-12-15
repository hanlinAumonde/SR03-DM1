package fr.utc.sr03.chat.controller;

import fr.utc.sr03.chat.dto.ChatroomDTO;
import fr.utc.sr03.chat.dto.ChatroomRequestDTO;
import fr.utc.sr03.chat.dto.ChatroomWithOwnerAndStatusDTO;
import fr.utc.sr03.chat.dto.DTOMapper;
import fr.utc.sr03.chat.dto.ModifyChatroomDTO;
import fr.utc.sr03.chat.dto.ModifyChatroomRequestDTO;
import fr.utc.sr03.chat.dto.UserDTO;
import fr.utc.sr03.chat.model.Chatroom;
import fr.utc.sr03.chat.model.User;
import fr.utc.sr03.chat.model.UserChatroomRelation;
import fr.utc.sr03.chat.service.implementations.ChatroomService;
import fr.utc.sr03.chat.service.implementations.UserChatroomRelationService;
import fr.utc.sr03.chat.service.implementations.UserService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@RestController
public class UserWebServiceController {

    private final Logger logger = LoggerFactory.getLogger(UserWebServiceController.class);

    //le nombre de chatrooms par page défault
    private static final int defaultPageSize = 5;

    @Resource
    private UserService userService;

    @Resource
    private ChatroomService chatroomService;

    @Resource
    private UserChatroomRelationService userChatroomRelationService;

    //Pour tous les méthodes ici, on va d'abord vérifier si l'utilisateur est connecté,
    //sinon on va retourner un code 401 (non autorisé), ce qui va permettre au front-end de rediriger l'utilisateur vers la page de login

    /**
     * Cette méthode permet d'obtenir le les informations de l'utilisateur connecté
     */
    @GetMapping("/users/logged")
    public ResponseEntity<UserDTO> getLoggedUser(HttpServletRequest request){
        if (userService.checkUserLoginStatus()) {
            User user = userService.getLoggedUser();
            return ResponseEntity.ok(DTOMapper.toUserDTO(user));
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(!(auth instanceof AnonymousAuthenticationToken)){
            //si l'utilisateur est connecté mais a ete supprimé par l'admin, on doit mauellement supprimer le contexte de login
            //delete login context
            SecurityContextHolder.getContext().setAuthentication(null);
            //invalidate http session
            request.getSession().invalidate();
            logger.info("user {} a ete supprime par l'admin", auth.getName());
        }
        return ResponseEntity.status(401).body(new UserDTO());
    }

    /**
     * Cette méthode permet d'obtenir tous les informations des autres utilisateurs sauf l'utilisateur connecté
     * Elle va etre utilisée dans le processus du planificateur de chatroom (inviter des utilisateurs)
     */
    @GetMapping("/user/users/other")
    public ResponseEntity<Page<UserDTO>> getOtherUsers(@RequestParam(defaultValue ="0")int page){
        if(userService.checkUserLoginStatus()){
            Page<User> users = userService.findAllOtherUsersNotAdminByPage(page, defaultPageSize, userService.getLoggedUser().getId());
            return ResponseEntity.ok(users.map(DTOMapper::toUserDTO));
        }
        return ResponseEntity.status(401).body(Page.empty());
    }

    /**
     * Cettet méthode permet créer une chatroom
     * Si une chatroom existe déjà (ou il y a des informations conflicts que les chatrooms existantes), on va retourner un code 409 (conflit)
     */
    @PostMapping("/user/chatrooms/")
    public ResponseEntity<Boolean> createChatroom(@RequestBody ChatroomRequestDTO chatroomRequestDTO){
        if(userService.checkUserLoginStatus()){
            User user = userService.getLoggedUser();
            Chatroom result = chatroomService.createChatroom(chatroomRequestDTO, user.getId());
            if(result.getId() != 0L){
                return ResponseEntity.ok(true);
            }else{
                return ResponseEntity.status(409).body(false);
            }
        }
        return ResponseEntity.status(401).body(false);
    }

    /**
     * Cette méthode permet d'obtenir tous les chatrooms créés par l'utilisateur connecté
     */
    @GetMapping("/user/users/{userId}/chatrooms/owned")
    public ResponseEntity<Page<ChatroomDTO>> getChatroomsOwnedOfPage(@PathVariable long userId, @RequestParam(defaultValue = "0")int page){
        if(userService.checkUserLoginStatus() && userId == userService.getLoggedUser().getId()){
            //Page<Chatroom> chatrooms = chatroomService.getChatroomsOwnedOrJoinedOfUserByPage(userId,true,page,defaultPageSize);
            Page<ChatroomDTO> chatrooms = chatroomService.getChatroomsOwnedOfUserByPage(userId,page,defaultPageSize);
        	return ResponseEntity.ok(chatrooms);
        }
        return ResponseEntity.status(401).body(Page.empty());
    }

    /**
     * Cette méthode permet d'obtenir tous les chatrooms auxquels l'utilisateur connecté a participé
     */
    @GetMapping("/user/users/{userId}/chatrooms/joined")
    public ResponseEntity<Page<ChatroomWithOwnerAndStatusDTO>> getChatroomsJoinedOfPage(@PathVariable long userId, @RequestParam(defaultValue = "0")int page){
    	if(userService.checkUserLoginStatus() && userId == userService.getLoggedUser().getId()){
    		return ResponseEntity.ok(chatroomService.getChatroomsJoinedOfUserByPage(userId, false, page, defaultPageSize));
    	}
    	return ResponseEntity.status(401).body(Page.empty());
    }

    /**
     * Cette méthode permet de supprimer une chatroom
     * Si il y a des conflits pendant la suppression, on va retourner un code 409 (conflit)
     */
    @DeleteMapping("/user/chatrooms/{chatroomId}")
    public ResponseEntity<Boolean> deleteChatroom(@PathVariable long chatroomId){
        if(userService.checkUserLoginStatus()){
            if(chatroomService.deleteChatRoom(chatroomId)){
                return ResponseEntity.ok(true);
            }else{
                return ResponseEntity.status(409).body(false);
            }
        }
        return ResponseEntity.status(401).body(false);
    }
    
    /**
     * Cette méthode permet d'obtenir les informations d'une chatroom
     * Si le chatroom n'existe pas, on va retourner un code 404 (non trouvé)
     */
    @GetMapping("/user/chatrooms/{chatroomId}")
    public ResponseEntity<ModifyChatroomDTO> getChatroomForModify(@PathVariable long chatroomId){
    	if(userService.checkUserLoginStatus()) {
    		Optional<Chatroom> chatroom = chatroomService.findChatroom(chatroomId);
			return chatroom.map(value -> ResponseEntity.ok(DTOMapper.toModifyChatroomDTO(value)))
					.orElseGet(() -> ResponseEntity.status(404).body(new ModifyChatroomDTO()));
    	}
    	return ResponseEntity.status(401).body(new ModifyChatroomDTO());
    }
    
    /**
     * Cette méthode permet d'obtenir tous les utilisateurs invités à une chatroom
     * on va vérifier si l'utilisateur connecté est le propriétaire de la chatroom
     * Si oui, on va retourner les utilisateurs invités à cette chatroom, sinon on va retourner un code 403 (interdit)
     */
    @GetMapping("/user/chatrooms/{chatroomId}/users/invited")
    public ResponseEntity<Page<UserDTO>> getUsersInvitedOfPage(@PathVariable long chatroomId, @RequestParam(defaultValue="0")int page){
        boolean checkOwner = chatroomService.checkUserIsOwnerOfChatroom(userService.getLoggedUser().getId(),chatroomId);
        if(userService.checkUserLoginStatus() && checkOwner){
            Page<User> users = userService.findUsersInvitedToChatroomByPage(chatroomId,page,defaultPageSize);
            return ResponseEntity.ok(users.map(DTOMapper::toUserDTO));
        }else if(!checkOwner){
            return ResponseEntity.status(403).body(Page.empty());
        }
        return ResponseEntity.status(401).body(Page.empty());
    }

    /**
     * Cette méthode permet d'obtenir tous les utilisateurs non invités à une chatroom
     * on va vérifier si l'utilisateur connecté est le propriétaire de la chatroom
     * Si oui, on va retourner les utilisateurs non invités, sinon on va retourner un code 403 (interdit)
     */
    @GetMapping("/user/chatrooms/{chatroomId}/users/non-invited")
    public ResponseEntity<Page<UserDTO>> getUsersNotInvitedOfPage(@PathVariable long chatroomId, @RequestParam(defaultValue="0")int page){
        boolean checkOwner = chatroomService.checkUserIsOwnerOfChatroom(userService.getLoggedUser().getId(),chatroomId);
        if(userService.checkUserLoginStatus() && checkOwner){
            Page<User> users = userService.findUsersNotInvitedToChatroomByPage(chatroomId,page,defaultPageSize);
            return ResponseEntity.ok(users.map(DTOMapper::toUserDTO));
        }else if(!checkOwner){
            return ResponseEntity.status(403).body(Page.empty());
        }
        return ResponseEntity.status(401).body(Page.empty());
    }
    
    @PutMapping("/user/chatrooms/{chatroomId}")
    public ResponseEntity<Boolean> updateChatroom(@PathVariable long chatroomId,@RequestBody ModifyChatroomRequestDTO chatroomRequest){
    	boolean checkOwner = chatroomService.checkUserIsOwnerOfChatroom(userService.getLoggedUser().getId(),chatroomId);
    	if(userService.checkUserLoginStatus() && checkOwner){
    		boolean res = chatroomService.updateChatroom(chatroomRequest, chatroomId);
            if(res) {
            	return ResponseEntity.ok(true);
            }else{
            	return ResponseEntity.status(409).body(false);
            }
        }else if(!checkOwner){
    		return ResponseEntity.status(403).body(false);
    	}
    	return ResponseEntity.status(401).body(false);
    }

    /**
     * Cette méthode permet d'obtenir tous les utilisateurs dans une chatroom
     * Elle va etre utilisé dans la page de chatroom pour afficher tous les utilisateurs dans cette chatroom
     */
    @GetMapping("/user/chatrooms/{chatroomId}/users")
    public ResponseEntity<List<UserDTO>> getAllUsersInChatroom(@PathVariable long chatroomId){
        if(userService.checkUserLoginStatus()){
            List<User> users = chatroomService.getAllUsersInChatroom(chatroomId);
            if(users.size() > 0) {
                return ResponseEntity.ok(
                        users.stream().map(DTOMapper::toUserDTO).toList()
                );
            }else {
                return ResponseEntity.status(500).body(new ArrayList<>());
            }
        }
        return ResponseEntity.status(401).body(new ArrayList<>());
    }
}
