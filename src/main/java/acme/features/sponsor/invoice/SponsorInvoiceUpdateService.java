
package acme.features.sponsor.invoice;

import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;

import org.assertj.core.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import acme.client.data.models.Dataset;
import acme.client.helpers.MomentHelper;
import acme.client.services.AbstractService;
import acme.entities.Invoice;
import acme.roles.Sponsor;

@Service
public class SponsorInvoiceUpdateService extends AbstractService<Sponsor, Invoice> {

	// Internal state ---------------------------------------------------------

	@Autowired
	private SponsorInvoiceRepository repository;

	// AbstractService interface ----------------------------------------------


	@Override
	public void authorise() {
		boolean status;
		int invoiceId;
		Invoice invoice;
		Sponsor sponsor;

		invoiceId = super.getRequest().getData("id", int.class);

		invoice = this.repository.findOneInvoiceById(invoiceId);
		sponsor = invoice == null ? null : invoice.getSponsor();
		status = invoice != null && invoice.isDraftMode() && super.getRequest().getPrincipal().hasRole(sponsor);

		super.getResponse().setAuthorised(status);
	}

	@Override
	public void load() {
		Invoice object;
		int id;

		id = super.getRequest().getData("id", int.class);
		object = this.repository.findOneInvoiceById(id);

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
		if (!super.getBuffer().getErrors().hasErrors("code"))
			super.state(this.repository.existsOtherByCodeAndId(object.getCode(), object.getId()), "code", "sponsor.invoice.form.error.duplicated");

		if (!super.getBuffer().getErrors().hasErrors("dueDate")) {
			Date minimumDeadline;

			minimumDeadline = MomentHelper.deltaFromMoment(object.getRegistrationTime(), 30, ChronoUnit.DAYS);
			super.state(MomentHelper.isAfter(object.getDueDate(), minimumDeadline), "dueDate", "sponsor.invoice.form.error.to-close-from-registration");
		}

		if (!super.getBuffer().getErrors().hasErrors("quantity")) {
			double before;
			double res;
			Collection<Invoice> invoices;

			invoices = this.repository.findManyInvoicesBySponsorshipId(object.getSponsorship().getId());
			before = this.repository.findOneInvoiceById(object.getId()).totalAmount().getAmount();
			res = object.totalAmount().getAmount() - before;
			for (Invoice i : invoices)
				res += i.totalAmount().getAmount();

			super.state(object.getQuantity().getAmount() > 0, "quantity", "sponsor.invoice.form.error.negative-salary");
			super.state(Arrays.asList(this.repository.findAcceptedCurrencies().split(",")).contains(object.getQuantity().getCurrency()), "quantity", "sponsor.invoice.form.error.invalid-currency");
			super.state(res <= object.getSponsorship().getAmount().getAmount(), "*", "sponsor.invoice.form.error.bad-total-amount");
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

		dataset = super.unbind(object, "code", "registrationTime", "dueDate", "quantity", "tax", "link", "draftMode");

		super.getResponse().addData(dataset);
	}

}
