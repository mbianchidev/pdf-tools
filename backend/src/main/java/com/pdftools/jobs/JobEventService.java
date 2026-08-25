package com.pdftools.jobs;

import com.pdftools.config.JobProperties;
import com.pdftools.jobs.api.JobResponse;
import com.pdftools.jobs.persistence.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class JobEventService {

    private static final Logger logger = LoggerFactory.getLogger(JobEventService.class);

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<Subscription>> emitters =
        new ConcurrentHashMap<>();
    private final long timeoutMillis;
    private final JobRepository jobRepository;
    private final JobMapper jobMapper;

    public JobEventService(
            JobProperties properties,
            JobRepository jobRepository,
            JobMapper jobMapper) {
        this.timeoutMillis = properties.getRetention().toMillis();
        this.jobRepository = jobRepository;
        this.jobMapper = jobMapper;
    }

    public SseEmitter subscribe(UUID jobId) {
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        Subscription subscription = new Subscription(emitter);
        emitters.computeIfAbsent(jobId, ignored -> new CopyOnWriteArrayList<>())
            .add(subscription);
        emitter.onCompletion(() -> remove(jobId, subscription));
        emitter.onTimeout(() -> remove(jobId, subscription));
        emitter.onError(ignored -> remove(jobId, subscription));

        jobRepository.findById(jobId)
            .map(jobMapper::toResponse)
            .ifPresentOrElse(
                state -> send(jobId, subscription, state),
                emitter::complete
            );
        return emitter;
    }

    public void publish(JobResponse state) {
        List<Subscription> subscribers = emitters.get(state.id());
        if (subscribers == null) {
            return;
        }
        for (Subscription subscription : subscribers) {
            send(state.id(), subscription, state);
        }
    }

    @Scheduled(fixedDelayString = "${pdf.jobs.sse-poll-interval:1s}")
    public void pollPersistedUpdates() {
        for (UUID jobId : emitters.keySet()) {
            jobRepository.findById(jobId)
                .map(jobMapper::toResponse)
                .ifPresentOrElse(
                    this::publish,
                    () -> complete(jobId)
                );
        }
    }

    private void send(UUID jobId, Subscription subscription, JobResponse state) {
        if (!subscription.advanceTo(state.version())) {
            return;
        }
        try {
            subscription.emitter.send(
                SseEmitter.event()
                    .name("job")
                    .id(Long.toString(state.version()))
                    .data(state)
            );
            if (state.status().isTerminal()) {
                subscription.emitter.complete();
            }
        } catch (IOException | IllegalStateException exception) {
            logger.debug("Removing closed SSE subscriber for job {}", jobId);
            remove(jobId, subscription);
        }
    }

    private void complete(UUID jobId) {
        List<Subscription> subscriptions = emitters.remove(jobId);
        if (subscriptions != null) {
            subscriptions.forEach(subscription -> subscription.emitter.complete());
        }
    }

    private void remove(UUID jobId, Subscription subscription) {
        emitters.computeIfPresent(jobId, (ignored, subscribers) -> {
            subscribers.remove(subscription);
            return subscribers.isEmpty() ? null : subscribers;
        });
    }

    private static final class Subscription {
        private final SseEmitter emitter;
        private final AtomicLong version = new AtomicLong(-1);

        private Subscription(SseEmitter emitter) {
            this.emitter = emitter;
        }

        private boolean advanceTo(long nextVersion) {
            long current;
            do {
                current = version.get();
                if (nextVersion <= current) {
                    return false;
                }
            } while (!version.compareAndSet(current, nextVersion));
            return true;
        }
    }
}
