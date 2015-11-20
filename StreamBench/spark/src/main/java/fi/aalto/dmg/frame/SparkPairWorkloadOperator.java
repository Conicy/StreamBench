package fi.aalto.dmg.frame;

import fi.aalto.dmg.exceptions.WorkloadException;
import fi.aalto.dmg.frame.functions.*;
import fi.aalto.dmg.util.TimeDurations;
import fi.aalto.dmg.util.Utils;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaPairDStream;
import scala.Tuple2;

/**
 * Created by yangjun.wang on 24/10/15.
 */
public class SparkPairWorkloadOperator<K,V> implements PairWorkloadOperator<K,V> {

    private static final long serialVersionUID = 7879350341179747221L;
    private JavaPairDStream<K,V> pairDStream;

    public SparkPairWorkloadOperator(JavaPairDStream<K, V> stream){
        this.pairDStream = stream;
    }

    @Override
    public SparkGroupedWorkloadOperator<K, V> groupByKey() {
        JavaPairDStream<K, Iterable<V>> newStream = pairDStream.groupByKey();
        return new SparkGroupedWorkloadOperator<>(newStream);
    }

    @Override
    public PairWorkloadOperator<K, V> reduceByKey(final ReduceFunction<V> fun, String componentId) {
        JavaPairDStream<K,V> newStream = pairDStream.reduceByKey(new ReduceFunctionImpl<>(fun));
        return new SparkPairWorkloadOperator<>(newStream);
    }

    @Override
    public <R> PairWorkloadOperator<K, R> mapValue(MapFunction<V, R> fun, String componentId) {
        JavaPairDStream<K,R> newStream = pairDStream.mapValues(new FunctionImpl<>(fun));
        return new SparkPairWorkloadOperator<>(newStream);
    }

    @Override
    public <R> PairWorkloadOperator<K, R> flatMapValue(FlatMapFunction<V, R> fun, String componentId) {
        JavaPairDStream<K,R> newStream = pairDStream.flatMapValues(new FlatMapValuesFunctionImpl<>(fun));
        return new SparkPairWorkloadOperator<>(newStream);
    }

    @Override
    public PairWorkloadOperator<K, V> filter(FilterFunction<Tuple2<K, V>> fun, String componentId) {
        JavaPairDStream<K,V> newStream = pairDStream.filter(new FilterFunctionImpl<>(fun));
        return new SparkPairWorkloadOperator<>(newStream);
    }

    @Override
    public PairWorkloadOperator<K, V> updateStateByKey(final ReduceFunction<V> fun, String componentId) {
        JavaPairDStream<K, V> cumulateStream = pairDStream.updateStateByKey(new UpdateStateFunctionImpl<>(fun));
        return new SparkPairWorkloadOperator<>(cumulateStream);
    }

    @Override
    public PairWorkloadOperator<K, V> reduceByKeyAndWindow(ReduceFunction<V> fun, String componentId, TimeDurations windowDuration) {
        return reduceByKeyAndWindow(fun, componentId, windowDuration, windowDuration);
    }

    @Override
    public PairWorkloadOperator<K, V> reduceByKeyAndWindow(ReduceFunction<V> fun, String componentId, TimeDurations windowDuration, TimeDurations slideDuration) {
        Duration windowDurations = Utils.timeDurationsToSparkDuration(windowDuration);
        Duration slideDurations = Utils.timeDurationsToSparkDuration(slideDuration);
        JavaPairDStream<K, V> accumulateStream = pairDStream.reduceByKeyAndWindow(new ReduceFunctionImpl<V>(fun), windowDurations, slideDurations);
        return new SparkPairWorkloadOperator<>(accumulateStream);
    }


    @Override
    public WindowedPairWorkloadOperator<K,V> window(TimeDurations windowDuration) {
        return window(windowDuration, windowDuration);
    }

    @Override
    public WindowedPairWorkloadOperator<K, V> window(TimeDurations windowDuration, TimeDurations slideDuration) {
        Duration windowDurations = Utils.timeDurationsToSparkDuration(windowDuration);
        Duration slideDurations = Utils.timeDurationsToSparkDuration(slideDuration);
        JavaPairDStream<K, V> windowedStream = pairDStream.window(windowDurations, slideDurations);
        return new SparkWindowedPairWorkloadOperator<>(windowedStream);
    }

    @Override
    public <R> PairWorkloadOperator<K, Tuple2<V, R>> join(String componentId, PairWorkloadOperator<K, R> joinStream, TimeDurations windowDuration, TimeDurations joinWindowDuration) throws WorkloadException {
        Duration windowDurations = Utils.timeDurationsToSparkDuration(windowDuration);
        Duration joinWindowDurations = Utils.timeDurationsToSparkDuration(joinWindowDuration);
        if(joinStream instanceof SparkPairWorkloadOperator) {
            SparkPairWorkloadOperator<K, R> joinSparkStream = ((SparkPairWorkloadOperator<K, R>) joinStream);
            JavaPairDStream<K, Tuple2<V, R>> joinedStream = pairDStream.window(windowDurations).join(joinSparkStream.pairDStream.window(joinWindowDurations));
            return new SparkPairWorkloadOperator<>(joinedStream);
        }
        throw new WorkloadException("Cast joinStrem to SparkPairWorkloadOperator failed");
    }

    @Override
    public void print() {
        this.pairDStream.print();
    }
}

