package io.simplesource.saga.model.messages;

import io.simplesource.data.NonEmptyList;
import io.simplesource.saga.model.action.ActionStatus;
import io.simplesource.saga.model.saga.Saga;
import io.simplesource.saga.model.saga.SagaError;
import io.simplesource.saga.model.saga.SagaStatus;
import lombok.Value;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public interface SagaStateTransition {

    @Value
    final class SetInitialState<A> implements SagaStateTransition {
        public final Saga<A> sagaState;

        @Override
        public <A> A cata(Function<SetInitialState<?>, A> f1, Function<SagaActionStatusChanged, A> f2, Function<SagaStatusChanged, A> f3, Function<TransitionList, A> f4) {
            return f1.apply(this);
        }
    }

    @Value
    final class SagaActionStatusChanged implements SagaStateTransition {
        public final UUID sagaId;
        public final UUID actionId;
        public final ActionStatus actionStatus;
        public final Optional<SagaError> actionError;

        @Override
        public <A> A cata(Function<SetInitialState<?>, A> f1, Function<SagaActionStatusChanged, A> f2, Function<SagaStatusChanged, A> f3, Function<TransitionList, A> f4) {
            return f2.apply(this);
        }
    }

    @Value
    final class SagaStatusChanged implements SagaStateTransition {
        public final UUID sagaId;
        public final SagaStatus sagaStatus;
        public final Optional<NonEmptyList<SagaError>> actionErrors;


        @Override
        public <A> A cata(Function<SetInitialState<?>, A> f1, Function<SagaActionStatusChanged, A> f2, Function<SagaStatusChanged, A> f3, Function<TransitionList, A> f4) {
            return f3.apply(this);
        }
    }

    @Value
    final class TransitionList implements SagaStateTransition {
        public final List<SagaActionStatusChanged> actions;

        @Override
        public <A> A cata(Function<SetInitialState<?>, A> f1, Function<SagaActionStatusChanged, A> f2, Function<SagaStatusChanged, A> f3, Function<TransitionList, A> f4) {
            return f4.apply(this);
        }
    }

    /**
     * Catamorphism over SagaStateTransition
     */
    <A> A cata(
            Function<SetInitialState<?>, A> f1,
            Function<SagaActionStatusChanged, A> f2,
            Function<SagaStatusChanged, A> f3,
            Function<TransitionList, A> f4
            );
}