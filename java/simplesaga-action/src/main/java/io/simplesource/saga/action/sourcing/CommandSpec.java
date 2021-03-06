package io.simplesource.saga.action.sourcing;

import io.simplesource.data.Result;
import io.simplesource.kafka.api.CommandSerdes;
import lombok.Value;

import java.util.function.Function;

/**
  * @param <A> - common representation form for all action commands (typically Json / GenericRecord for Avro)
  * @param <I> - intermediate decoded input type (that can easily be converted both K and C)
  * @param <K> - aggregate key
  * @param <C> - simple sourcing command type
  */
@Value
public final class CommandSpec<A, I, K, C> {
    public final String actionType;
    public final Function<A, Result<Throwable, I>> decode;
    public final Function<I, C> commandMapper;
    public final Function<I, K> keyMapper;
    public final CommandSerdes<K, C> commandSerdes;
    public final String aggregateName;
    public final long timeOutMillis;

}
