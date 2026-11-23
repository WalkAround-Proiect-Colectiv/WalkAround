package services.applicationapi.tripplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import services.applicationapi.tripplan.controllers.GroupChatController;
import services.applicationapi.tripplan.dto.GroupChatMessageResponse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import services.applicationapi.tripplan.configurations.GroupChatProperties;
import services.applicationapi.tripplan.dto.GroupChatMessageRequest;
import services.applicationapi.tripplan.dto.GroupChatReplyResponse;
import services.applicationapi.tripplan.model.GroupChatImage;
import services.applicationapi.tripplan.model.GroupChatMessage;
import services.applicationapi.tripplan.repository.*;
import services.applicationapi.tripplan.service.groupchat.*;
import services.applicationapi.tripplan.service.exceptions.*;
import tools.jackson.databind.json.JsonMapper;

class GroupChatReplyTest {
    private final UUID group = UUID.randomUUID();
    private final UUID author = UUID.randomUUID();
    private final UUID parentId = UUID.randomUUID();
    private final UUID messageId = UUID.randomUUID();
    private final GroupChatRepository repository = mock(GroupChatRepository.class);
    private final GroupChatPollRepository polls = mock(GroupChatPollRepository.class);
    private final GroupChatImageRepository images = mock(GroupChatImageRepository.class);
    private final GroupChatImageStorage storage = mock(GroupChatImageStorage.class);
    private final GroupChatImageNormalizer normalizer = mock(GroupChatImageNormalizer.class);
    private final TripPlanGroupMemberRepository members = mock(TripPlanGroupMemberRepository.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private GroupChatServiceImpl service;

    @BeforeEach
    void service() {
        var properties = mock(GroupChatProperties.class);
        when(properties.maxMessageLength()).thenReturn(2000);
        when(properties.pageSize()).thenReturn(50);
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(members.existsByGroup_IdAndUser_Id(group, author)).thenReturn(true);
        when(repository.findById(any())).thenReturn(Optional.empty());
        when(repository.findById(parentId)).thenReturn(Optional.of(new GroupChatMessage(
                parentId, group, 1, author, "Walker", "Meet at the entrance", "TEXT", Instant.EPOCH)));
        service = new GroupChatServiceImpl(repository, polls, images, storage, normalizer,
                transactions, members, properties, null, null, json);
    }

    @Test
    void quotesTheParentWithAShortPlainTextPreview() {
        var parent = message("TEXT", "  Meet\n by the entrance  ");
        var quote = GroupChatReplyResponse.from(parent);
        assertThat(quote.messageId()).isEqualTo(parent.id());
        assertThat(quote.senderName()).isEqualTo("Walker");
        assertThat(quote.preview()).isEqualTo("Meet by the entrance");
    }

    @Test
    void imageQuotesNeverContainSignedMediaUrlsAndPollsShowTheQuestion() {
        assertThat(GroupChatReplyResponse.from(message("IMAGE", "https://private.example/photo?signature=test")).preview()).isEqualTo("Photo");
        assertThat(GroupChatReplyResponse.from(message("POLL", "Where shall we meet?")).preview()).isEqualTo("Poll: Where shall we meet?");
    }

    @Test
    void truncationDoesNotSplitEmojiSurrogatePairs() {
        String emoji = new String(Character.toChars(0x1f600));
        var preview = GroupChatReplyResponse.from(message("TEXT", emoji.repeat(170))).preview();
        assertThat(preview.codePointCount(0, preview.length())).isEqualTo(160);
        assertThat(preview).isEqualTo(emoji.repeat(160));
    }

    @Test
    void newTextRepliesPersistParentAndSerializeTheQuoteForLiveDelivery() {
        var child = child("TEXT");
        when(repository.insert(eq(messageId), eq(group), eq(author), eq("Answer"), eq("TEXT"), any(), eq(parentId))).thenReturn(child);
        when(repository.eventSequence(messageId)).thenReturn(2L);
        var result = service.send(group, author, request(parentId));
        assertThat(result.replyTo().messageId()).isEqualTo(parentId);
        var payload = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(repository).setPayload(eq(messageId), payload.capture());
        assertThat(json.readTree(payload.getValue()).get("replyTo").get("preview").asString()).isEqualTo("Meet at the entrance");
    }

    @Test
    void retriesCannotChangeOrRemoveTheParentAndDoNotInsertAnotherMessage() {
        when(repository.findById(messageId)).thenReturn(Optional.of(child("TEXT")));
        assertThat(service.send(group, author, request(parentId)).replyTo().messageId()).isEqualTo(parentId);
        assertThatThrownBy(() -> service.send(group, author, request(null))).isInstanceOf(GroupChatConflictException.class);
        assertThatThrownBy(() -> service.send(group, author, request(UUID.randomUUID()))).isInstanceOf(GroupChatConflictException.class);
        verify(repository, never()).insert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void missingAndCrossGroupParentsAreRejectedBeforeAnyWrite() {
        assertThatThrownBy(() -> service.send(group, author, request(UUID.randomUUID())))
                .isInstanceOf(GroupChatInvalidMessageException.class);
        when(repository.findById(parentId)).thenReturn(Optional.of(new GroupChatMessage(
                parentId, UUID.randomUUID(), 1, author, "Walker", "Other group", "TEXT", Instant.EPOCH)));
        assertThatThrownBy(() -> service.send(group, author, request(parentId))).isInstanceOf(GroupChatInvalidMessageException.class);
        verify(repository, never()).insert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void nonMembersCannotReadOrReply() {
        UUID outsider = UUID.randomUUID();
        assertThatThrownBy(() -> service.send(group, outsider, request(parentId))).isInstanceOf(TripGroupNotFoundException.class);
        assertThatThrownBy(() -> service.messages(group, outsider, null, null, null)).isInstanceOf(TripGroupNotFoundException.class);
    }

    @Test
    void historyAndCatchUpIncludeQuotesWhoseParentsAreNotInThePage() {
        when(repository.latest(group, 50)).thenReturn(List.of(child("TEXT")));
        var page = service.messages(group, author, null, null, null);
        assertThat(page).hasSize(1);
        assertThat(page.getFirst().replyTo().messageId()).isEqualTo(parentId);
        when(repository.eventsAfter(group, 0, 50)).thenReturn(List.of(new GroupChatRepository.EventReference(2, messageId)));
        when(repository.findById(messageId)).thenReturn(Optional.of(child("TEXT")));
        assertThat(service.events(group, author, 0, null).getFirst().message().replyTo().messageId()).isEqualTo(parentId);
    }

    @Test
    void imageRetryIncludesTheParentAndInvalidParentNeverUploads() {
        MultipartFile file = mock(MultipartFile.class);
        when(normalizer.normalize(file)).thenReturn(new GroupChatImageNormalizer.NormalizedImage(new byte[]{1}, "digest", 1, 1));
        assertThatThrownBy(() -> service.sendImage(group, author, messageId, "Answer", file, UUID.randomUUID()))
                .isInstanceOf(GroupChatInvalidMessageException.class);
        verify(storage, never()).upload(any(), any(), any());
        when(repository.findById(messageId)).thenReturn(Optional.of(child("IMAGE")));
        when(images.findByMessageId(messageId)).thenReturn(Optional.of(new GroupChatImage(messageId, "file", "/photo", "digest", 1, 1)));
        when(storage.signedUrl("/photo")).thenReturn("https://images.example/photo");
        assertThat(service.sendImage(group, author, messageId, "Answer", file, parentId).replyTo().messageId()).isEqualTo(parentId);
        assertThatThrownBy(() -> service.sendImage(group, author, messageId, "Answer", file, null)).isInstanceOf(GroupChatConflictException.class);
    }

    @Test
    void jsonReplyContractBindsTheParentAndReturnsItsPreview() throws Exception {
        var chat = mock(GroupChatService.class);
        var parent = repository.findById(parentId).orElseThrow();
        when(chat.send(any(), any(), any())).thenReturn(GroupChatMessageResponse.from(
                child("TEXT"), null, null, GroupChatReplyResponse.from(parent)));
        var mvc = MockMvcBuilders.standaloneSetup(new GroupChatController(chat))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        var jwt = Jwt.withTokenValue("unit-test-fixture").header("alg", "none").subject(author.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        try {
            mvc.perform(post("/api/trip-plan-groups/" + group + "/chat/messages").contentType("application/json")
                    .content(json.writeValueAsString(request(parentId))))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.replyTo.messageId").value(parentId.toString()))
                    .andExpect(jsonPath("$.replyTo.preview").value("Meet at the entrance"));
            verify(chat).send(eq(group), eq(author), argThat(request -> parentId.equals(request.replyToMessageId())));
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void imageMultipartReplyContractPassesTheParentToTheService() throws Exception {
        var chat = mock(GroupChatService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new GroupChatController(chat))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        var jwt = Jwt.withTokenValue("unit-test-fixture").header("alg", "none").subject(author.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        try {
            mvc.perform(multipart("/api/trip-plan-groups/" + group + "/chat/messages/images")
                    .file(new MockMultipartFile("image", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3}))
                    .param("messageId", messageId.toString()).param("caption", "Answer").param("replyToMessageId", parentId.toString()))
                    .andExpect(status().isCreated());
            verify(chat).sendImage(eq(group), eq(author), eq(messageId), eq("Answer"), any(), eq(parentId));
        } finally { SecurityContextHolder.clearContext(); }
    }

    private GroupChatMessageRequest request(UUID parent) {
        return new GroupChatMessageRequest(messageId, "Answer", "TEXT", null, parent);
    }

    private GroupChatMessage child(String type) {
        return new GroupChatMessage(messageId, group, 2, author, "Walker", "Answer", type, Instant.EPOCH, parentId);
    }

    private GroupChatMessage message(String type, String body) {
        return new GroupChatMessage(UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), "Walker", body, type, Instant.EPOCH);
    }
}