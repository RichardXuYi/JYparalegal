package com.jyfc.backend.module.sign.service;

import com.jyfc.backend.core.exception.BusinessException;
import com.jyfc.backend.module.auth.entity.UserEntity;
import com.jyfc.backend.module.auth.repository.UserRepository;
import com.jyfc.backend.module.sign.entity.SignPartyEntity;
import com.jyfc.backend.module.sign.entity.SignProviderEventEntity;
import com.jyfc.backend.module.sign.entity.SignTaskEntity;
import com.jyfc.backend.module.sign.repository.SignPartyRepository;
import com.jyfc.backend.module.sign.repository.SignProviderEventRepository;
import com.jyfc.backend.module.sign.repository.SignTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消费控制平面转发来的 e签宝事件。浏览器不能调用这里把任务标成已签。
 */
@Service
public class EsignCallbackService {

    private final SignTaskRepository taskRepository;
    private final SignPartyRepository partyRepository;
    private final SignProviderEventRepository eventRepository;
    private final SignTaskStateMachine stateMachine;
    private final UserRepository userRepository;

    public EsignCallbackService(SignTaskRepository taskRepository, SignPartyRepository partyRepository,
                                SignProviderEventRepository eventRepository, SignTaskStateMachine stateMachine,
                                UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.partyRepository = partyRepository;
        this.eventRepository = eventRepository;
        this.stateMachine = stateMachine;
        this.userRepository = userRepository;
    }

    @Transactional
    public void apply(String eventKey, String flowId, String action, String account, String reason) {
        if (eventKey == null || eventKey.isBlank() || flowId == null || flowId.isBlank()) {
            throw new BusinessException("回调缺少事件号或流程号");
        }
        if (eventRepository.existsByEventKey(eventKey)) {
            return;
        }
        SignProviderEventEntity ev = new SignProviderEventEntity();
        ev.setEventKey(eventKey);
        ev.setFlowId(flowId);
        ev.setAction(action == null ? "IGNORE" : action);
        eventRepository.save(ev);

        SignTaskEntity task = taskRepository.findByProviderFlowId(flowId).orElse(null);
        if (task == null || action == null || "IGNORE".equals(action) || "READ".equals(action)) {
            return;
        }
        switch (action) {
            case "SIGNER_SIGNED" -> markSigned(task, account);
            case "FLOW_COMPLETE" -> completeIfReady(task, reason);
            case "EXPIRE" -> {
                if ("SIGNING".equals(task.getStatus())) {
                    stateMachine.transfer(task.getId(), "EXPIRE", "SYSTEM", null, "e签宝过期");
                }
            }
            case "REVOKE" -> {
                if (List.of("CREATED", "FILLING", "FINALIZING", "SIGNING").contains(task.getStatus())) {
                    stateMachine.transfer(task.getId(), "REVOKE", "SYSTEM", null, reason == null ? "厂商侧撤销" : reason);
                }
            }
            case "REJECT" -> reject(task, account, reason);
            case "VOID_FINISH" -> {
                if ("VOIDING".equals(task.getStatus())) {
                    stateMachine.transfer(task.getId(), "FINISH_VOID", "SYSTEM", null, reason);
                }
            }
            default -> {
            }
        }
    }

    private void markSigned(SignTaskEntity task, String account) {
        SignPartyEntity party = match(task.getId(), account);
        if (party == null || "SIGNED".equals(party.getPartyStatus())) {
            return;
        }
        party.setPartyStatus("SIGNED");
        party.setSignedAt(LocalDateTime.now());
        partyRepository.save(party);
        completeIfReady(task, "e签宝签署完成");
    }

    private void completeIfReady(SignTaskEntity task, String reason) {
        if (!"SIGNING".equals(task.getStatus())) {
            return;
        }
        List<SignPartyEntity> signers = partyRepository.findByTaskIdOrderByIdAsc(task.getId()).stream()
                .filter(p -> "SIGNER".equals(p.getPartyRole()))
                .toList();
        if (signers.isEmpty() || signers.stream().anyMatch(p -> !"SIGNED".equals(p.getPartyStatus()))) {
            return;
        }
        if ("MANUAL".equals(task.getFinalizeMode())) {
            return;
        }
        stateMachine.transfer(task.getId(), "COMPLETE", "SYSTEM", null, reason == null ? "流程结束" : reason);
    }

    private void reject(SignTaskEntity task, String account, String reason) {
        SignPartyEntity party = match(task.getId(), account);
        if (party != null && !"REJECTED".equals(party.getPartyStatus())) {
            party.setPartyStatus("REJECTED");
            partyRepository.save(party);
        }
        if (!"SIGNING".equals(task.getStatus())) {
            return;
        }
        String why = (reason == null || reason.isBlank()) ? "签署人在签署页拒签" : reason;
        SignTaskEntity rejected = stateMachine.transfer(task.getId(), "REJECT", "SYSTEM", null, why);
        stateMachine.transfer(rejected.getId(), "AUTO_TERMINATE", "SYSTEM", null, "required signer rejected");
    }

    private SignPartyEntity match(Long taskId, String account) {
        if (account == null || account.isBlank()) {
            return null;
        }
        String key = account.trim();
        List<SignPartyEntity> parties = partyRepository.findByTaskIdOrderByIdAsc(taskId);
        for (SignPartyEntity p : parties) {
            if (key.equals(p.getExternalPhone()) || key.equalsIgnoreCase(nullToEmpty(p.getExternalEmail()))) {
                return p;
            }
            if (p.getUserId() != null) {
                UserEntity user = userRepository.findById(p.getUserId()).orElse(null);
                if (user != null && (key.equals(user.getPhone()) || key.equalsIgnoreCase(nullToEmpty(user.getEmail())))) {
                    return p;
                }
            }
        }
        return null;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
