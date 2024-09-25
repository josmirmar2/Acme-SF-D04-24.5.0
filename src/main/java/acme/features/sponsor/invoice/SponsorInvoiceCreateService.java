
package acme.features.sponsor.invoice;

import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.assertj.core.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import acme.client.data.models.Dataset;
import acme.client.helpers.MomentHelper;
import acme.client.services.AbstractService;
import acme.entities.Invoice;
import acme.entities.Sponsorship;
import acme.roles.Sponsor;

@Service
public class SponsorInvoiceCreateService extends AbstractService<Sponsor, Invoice> {

	// Internal state ---------------------------------------------------------

	@Autowired
	private SponsorInvoiceRepository repository;

	// AbstractService interface ----------------------------------------------


	@Override
	public void authorise() {
		boolean status;
		int masterId;
		Sponsor sponsor;
		Sponsorship sponsorship;

		masterId = super.getRequest().getData("masterId", int.class);
		sponsorship = this.repository.findOneSponsorshipById(masterId);
		sponsor = sponsorship == null ? null : sponsorship.getSponsor();
		status = sponsorship != null && sponsorship.isDraftMode() && super.getRequest().getPrincipal().hasRole(sponsor);

		super.getResponse().setAuthorised(status);
	}

	@Override
	public void load() {
		Invoice object;
		Sponsor sponsor;
		int masterId;
		Sponsorship sponsorship;

		sponsor = this.repository.findOneSponsorById(super.getRequest().getPrincipal().getActiveRoleId());

		masterId = super.getRequest().getData("masterId", int.class);
		sponsorship = this.repository.findOneSponsorshipById(masterId);

		object = new Invoice();
		object.setDraftMode(true);
		object.setSponsorship(sponsorship);
		object.setSponsor(sponsor);
		object.setRegistrationTime(MomentHelper.getCurrentMoment());

		super.getBuffer().addData(object);
	}

	@Override
	public void bind(final Invoice object) {
		assert object != null;

		super.bind(object, "code", "registrationTime", "dueDate", "quantity", "tax", "link");
	}

	@Override
	public void validate(final Invoice object) {
		assert object != null;
		if (!super.getBuffer().getErrors().hasErrors("code")) {
			Invoice existing;

			existing = this.repository.findOneInvoiceByCode(object.getCode());
			super.state(existing == null, "code", "sponsor.invoice.form.error.duplicated");
		}

		if (!super.getBuffer().getErrors().hasErrors("dueDate")) {
			Date maximumDeadline;

			if (!super.getBuffer().getErrors().hasErrors("registrationTime")) {
				maximumDeadline = MomentHelper.deltaFromMoment(object.getRegistrationTime(), 1, ChronoUnit.MONTHS);
				super.state(MomentHelper.isAfter(object.getDueDate(), maximumDeadline), "dueDate", "sponsor.invoice.form.error.to-close-from-registration");

			} else
				super.state(false, "dueDate", "sponsor.invoice.form.error.incorrect-registration-time");
		}

		if (!super.getBuffer().getErrors().hasErrors("quantity")) {
			super.state(object.getQuantity().getAmount() > 0, "quantity", "sponsor.invoice.form.error.negative-salary");
			super.state(Arrays.asList(this.repository.findAcceptedCurrencies().split(",")).contains(object.getQuantity().getCurrency()), "quantity", "sponsor.invoice.form.error.invalid-currency");
		}
	}

	@Override
	public void perform(final Invoice object) {
		assert object != null;

		this.repository.save(object);
	}

	@Override
	public void unbind(final Invoice object) {
		assert object != null;

		Dataset dataset;

		dataset = super.unbind(object, "code", "registrationTime", "dueDate", "quantity", "tax", "link");
		dataset.put("masterId", super.getRequest().getData("masterId", int.class));
		dataset.put("draftMode", object.getSponsorship().isDraftMode());

		super.getResponse().addData(dataset);
	}

}
