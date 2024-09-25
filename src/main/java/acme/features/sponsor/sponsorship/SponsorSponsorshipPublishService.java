
package acme.features.sponsor.sponsorship;

import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import acme.client.data.datatypes.Money;
import acme.client.data.models.Dataset;
import acme.client.helpers.MomentHelper;
import acme.client.services.AbstractService;
import acme.client.views.SelectChoices;
import acme.entities.Invoice;
import acme.entities.Project;
import acme.entities.Sponsorship;
import acme.enumerated.TypeOfSponsorship;
import acme.roles.Sponsor;

@Service
public class SponsorSponsorshipPublishService extends AbstractService<Sponsor, Sponsorship> {

	// Internal state ---------------------------------------------------------

	@Autowired
	private SponsorSponsorshipRepository repository;

	// AbstractService interface ----------------------------------------------


	@Override
	public void authorise() {
		boolean status;
		int masterId;
		Sponsor sponsor;
		Sponsorship sponsorship;

		masterId = super.getRequest().getData("id", int.class);
		sponsorship = this.repository.findOneSponsorshipById(masterId);
		sponsor = sponsorship == null ? null : sponsorship.getSponsor();
		status = sponsorship != null && sponsorship.isDraftMode() && super.getRequest().getPrincipal().hasRole(sponsor);

		super.getResponse().setAuthorised(status);
	}

	@Override
	public void load() {
		Sponsorship object;
		int id;

		id = super.getRequest().getData("id", int.class);
		object = this.repository.findOneSponsorshipById(id);

		super.getBuffer().addData(object);
	}

	@Override
	public void bind(final Sponsorship object) {
		assert object != null;
		int projectId;
		Project project;

		projectId = super.getRequest().getData("project", int.class);
		project = this.repository.findOneProjectById(projectId);

		super.bind(object, "code", "moment", "startDate", "endDate", "email", "link", "type", "amount");
		object.setProject(project);
	}

	@Override
	public void validate(final Sponsorship object) {
		assert object != null;
		super.state(this.repository.allInvoicesPublishedBySponsorshipId(object.getId()), "*", "sponsor.sponsorship.form.error.publish-invoices");

		if (!super.getBuffer().getErrors().hasErrors("code")) {
			Sponsorship existing;
			existing = this.repository.findOneSponsorshipByCode(object.getCode());

			super.state(existing == null || existing.getId() == object.getId(), "code", "sponsor.sponsorship.form.error.duplicated");
		}

		if (!super.getBuffer().getErrors().hasErrors("startDate")) {
			Date minimumDeadline;

			if (!super.getBuffer().getErrors().hasErrors("moment")) {
				super.state(MomentHelper.isAfterOrEqual(object.getStartDate(), object.getMoment()), "startDate", "sponsor.sponsorship.form.error.too-close-moment");

				minimumDeadline = object.getEndDate() == null ? null : MomentHelper.deltaFromMoment(object.getEndDate(), 1, ChronoUnit.MONTHS);
				super.state(object.getEndDate() == null || MomentHelper.isBefore(object.getStartDate(), minimumDeadline), "startDate", "sponsor.sponsorship.form.error.duration-more-time");
			} else
				super.state(false, "startDate", "sponsor.sponsorship.form.error.invalid-moment");

		}

		if (!super.getBuffer().getErrors().hasErrors("endDate")) {
			Date maximumDeadline;

			if (!super.getBuffer().getErrors().hasErrors("moment")) {
				super.state(MomentHelper.isAfterOrEqual(object.getEndDate(), object.getMoment()), "endDate", "sponsor.sponsorship.form.error.too-close-moment");

				maximumDeadline = object.getStartDate() == null ? null : MomentHelper.deltaFromMoment(object.getStartDate(), 1, ChronoUnit.MONTHS);
				super.state(object.getStartDate() == null || MomentHelper.isAfter(object.getEndDate(), maximumDeadline), "endDate", "sponsor.sponsorship.form.error.duration-more-time");
			} else
				super.state(false, "endDate", "sponsor.sponsorship.form.error.invalid-moment");
		}

		if (!super.getBuffer().getErrors().hasErrors("project")) {
			Boolean isDraftMode = this.repository.projectIsDraftMode(object.getProject().getId());
			super.state(!isDraftMode, "project", "sponsor.sponsorship.form.error.not-published-project");
		}

		Collection<Invoice> invoices = this.repository.findManyInvoicesBySponsorshipId(object.getId());
		double sumTotal = 0.0;
		if (object.getAmount() != null) {
			String systemCurrency;
			for (Invoice i : invoices) {
				systemCurrency = this.repository.findSystemConfiguration().getSystemCurrency();
				sumTotal += i.totalAmount().getAmount() * this.repository.findMoneyConvertByMoneyCurrency(systemCurrency);
			}

			double factor = Math.pow(10, 2);
			sumTotal = Math.round(sumTotal * factor) / factor;
		} else
			sumTotal = -10000.00;

		if (!super.getBuffer().getErrors().hasErrors("amount")) {
			Money amount;
			amount = object.getAmount();

			super.state(amount.getAmount() >= 0, "amount", "sponsor.sponsorship.form.error.negative-amount");
			super.state(amount.getAmount() * this.repository.findMoneyConvertByMoneyCurrency(amount.getCurrency()) == sumTotal, "amount", "sponsor.sponsorship.form.error.invoices-amount");
			super.state(amount.getCurrency().equals("EUR") || amount.getCurrency().equals("USD") || amount.getCurrency().equals("GBP"), "amount", "sponsor.sponsorship.form.error.wrong-currency");
		}
	}

	@Override
	public void perform(final Sponsorship object) {
		assert object != null;
		object.setDraftMode(false);

		this.repository.save(object);
	}

	@Override
	public void unbind(final Sponsorship object) {
		assert object != null;

		Collection<Project> projects;
		SelectChoices choices;
		SelectChoices choicesType;
		Dataset dataset;

		projects = this.repository.findAllPublishedProjects();
		choices = SelectChoices.from(projects, "code", object.getProject());
		choicesType = SelectChoices.from(TypeOfSponsorship.class, object.getType());

		dataset = super.unbind(object, "code", "moment", "startDate", "endDate", "email", "link", "type", "draftMode", "amount");
		dataset.put("project", choices.getSelected().getKey());
		dataset.put("projects", choices);
		dataset.put("type", choicesType.getSelected().getKey());
		dataset.put("types", choicesType);

		super.getResponse().addData(dataset);
	}

}
