package io.simplesource.saga.saga.app;


import io.simplesource.data.NonEmptyList;
import io.simplesource.data.Sequence;
import io.simplesource.saga.model.action.ActionStatus;
import io.simplesource.saga.model.action.SagaAction;
import io.simplesource.saga.model.messages.SagaStateTransition;
import io.simplesource.saga.model.saga.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

final class SagaUtils {
    static Logger logger = LoggerFactory.getLogger(SagaUtils.class);

    private static <A> boolean sagaUndoesPending(Saga<A> sagaState) {
        return sagaState.actions.values()
                .stream()
                .map(a -> a.status)
                .anyMatch(s -> s == ActionStatus.Completed || s == ActionStatus.InUndo);
    }

    static <A> boolean failedAction(Saga<A> sagaState) {
        return sagaState.actions.values()
                .stream()
                .anyMatch(a -> a.status.equals(ActionStatus.Failed));
    }

    static <A> boolean sagaInFailure(Saga<A> sagaState) {
        return failedAction(sagaState) && sagaUndoesPending(sagaState);
    }

    static <A> boolean sagaFailed(Saga<A> sagaState) {
        return failedAction(sagaState) && !sagaUndoesPending(sagaState);
    }

    static <A> boolean sagaCompleted(Saga<A> sagaState) {
        for (SagaAction<A> a : sagaState.actions.values()) {
            if (a.status != ActionStatus.Completed)
                return false;
        }
        return true;
    }

    static <A> List<SagaActionExecution<A>> getNextActions(Saga<A> sagaState) {
        if (sagaState.status == SagaStatus.InProgress) {
            Set<UUID> doneKeys = sagaState.actions
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getValue().status == ActionStatus.Completed)
                    .map(Map.Entry::getValue)
                    .map(x -> x.actionId)
                    .collect(Collectors.toSet());
            List<SagaActionExecution<A>> pendingActions = sagaState.actions
                    .values()
                    .stream()
                    .filter(action -> action.status == ActionStatus.Pending && doneKeys.containsAll(action.dependencies))
                    .map(a -> new SagaActionExecution<A>(a.actionId, a.actionType, Optional.of(a.command), ActionStatus.InProgress))
                    .collect(Collectors.toList());
            return pendingActions;
        } else if (sagaState.status == SagaStatus.InFailure) {
            // reverse the arrows in the dependency graph
            Map<UUID, Set<UUID>> reversed = new HashMap<>();
            sagaState.actions.values().forEach(action -> {
                action.dependencies.forEach(dep -> {
                    reversed.putIfAbsent(dep, new HashSet<>());
                    Set<UUID> revSet = reversed.get(dep);
                    revSet.add(action.actionId);
                });
            });

            Set<UUID> undoneKeys = sagaState.actions.entrySet().stream()
                    .filter(entry -> {
                        ActionStatus status = entry.getValue().status;
                        return status != ActionStatus.InUndo && status != ActionStatus.Completed;
                    })
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            Map<UUID, SagaAction<A>> pendingUndoes = sagaState.actions
                    .entrySet()
                    .stream()
                    .filter(entry -> {
                        SagaAction<A> action = entry.getValue();
                        return action.status == ActionStatus.Completed &&
                                undoneKeys.containsAll(reversed.getOrDefault(action.actionId, new HashSet<>()));
                    })
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            List<SagaActionExecution<A>> pendingExecutions = pendingUndoes.values()
                    .stream()
                    .map(a -> {
                        ActionStatus status = a.undoCommand.map(c -> ActionStatus.InUndo).orElse(ActionStatus.UndoBypassed);
                        return new SagaActionExecution<>(a.actionId, a.actionType, a.undoCommand, status);

                    })
                    .collect(Collectors.toList());

            return pendingExecutions;
        }
        return new ArrayList<>();
    }

    static <A> Saga<A> applyTransition(SagaStateTransition t, Saga<A> s) {
        if (t instanceof SagaStateTransition.SetInitialState) {
            Saga<A> i = ((SagaStateTransition.SetInitialState<A>) t).sagaState;
            return Saga.of(i.sagaId, i.actions, SagaStatus.InProgress, Sequence.first());
        }
        if (t instanceof SagaStateTransition.SagaActionStatusChanged) {
            SagaStateTransition.SagaActionStatusChanged st = ((SagaStateTransition.SagaActionStatusChanged) t);
            SagaAction<A> oa = s.actions.getOrDefault(st.actionId, null);
            if (oa == null) {
                logger.error("SagaAction with ID {} could not be found", st.actionId);
                return s;
            }
            ActionStatus newStatus =  st.actionStatus;
            if (oa.status == ActionStatus.InUndo) {
                if (st.actionStatus == ActionStatus.Completed) newStatus = ActionStatus.Undone;
                else if (st.actionStatus == ActionStatus.Failed) newStatus = ActionStatus.UndoFailed;
            }
            SagaAction<A> action =
                    new SagaAction<>(oa.actionId, oa.actionType, oa.command, oa.undoCommand, oa.dependencies, newStatus, st.actionError);

            // TODO: add a MapUtils updated
            Map<UUID, SagaAction<A>> actionMap = new HashMap<>();
            s.actions.forEach((k, v) -> actionMap.put(k, k.equals(st.actionId) ? action : v));
            return s.updated(actionMap, s.status, s.sagaError);
        }
        if (t instanceof SagaStateTransition.SagaStatusChanged) {
            // TODO: add saga errors
            SagaStateTransition.SagaStatusChanged st = ((SagaStateTransition.SagaStatusChanged) t);
            return s.updated(st.sagaStatus, st.actionErrors.map(NonEmptyList::head));
        }
        if (t instanceof SagaStateTransition.TransitionList) {
            Saga<A> sNew = s;
            SagaStateTransition.TransitionList tl = ((SagaStateTransition.TransitionList) t);
            for (SagaStateTransition change: tl.actions) {
                sNew = applyTransition(change, sNew);
            }
            return sNew;
        }
        return s;
    }
}
