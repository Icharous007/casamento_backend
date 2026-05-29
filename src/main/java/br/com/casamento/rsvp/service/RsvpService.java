package br.com.casamento.rsvp.service;

import br.com.casamento.common.exception.AppException;
import br.com.casamento.domain.event.Event;
import br.com.casamento.domain.guest.Guest;
import br.com.casamento.domain.rsvp.Rsvp;
import br.com.casamento.rsvp.dto.RsvpRequest;
import br.com.casamento.rsvp.dto.RsvpResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@ApplicationScoped
public class RsvpService {

    @Transactional
    public RsvpResponse upsert(Guest guest, Event event, RsvpRequest request) {
        if (event.rsvpDeadlineAt != null && OffsetDateTime.now().isAfter(event.rsvpDeadlineAt)) {
            throw AppException.rsvpDeadlineExpired();
        }

        Rsvp rsvp = Rsvp.findByGuest(guest);
        boolean isNew = rsvp == null;

        if (isNew) {
            rsvp = new Rsvp();
            rsvp.guest = guest;
            rsvp.event = event;
        }

        rsvp.response = request.attendanceStatus();
        rsvp.dietaryRestrictions = request.dietaryRestrictions();
        rsvp.allergies = request.allergies();
        rsvp.additionalInfo = request.additionalInfo();

        if (isNew) {
            rsvp.persist();
        }

        return new RsvpResponse(
                guest.id.toString(),
                rsvp.response,
                rsvp.respondedAt,
                rsvp.updatedAt
        );
    }

    public List<Rsvp> listByEvent(Event event) {
        return Rsvp.find("event = ?1 ORDER BY respondedAt DESC", event).list();
    }
}
