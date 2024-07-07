
package acme.features.auditor.auditRecord;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import acme.client.data.accounts.Principal;
import acme.client.data.models.Dataset;
import acme.client.helpers.MomentHelper;
import acme.client.services.AbstractService;
import acme.client.views.SelectChoices;
import acme.entities.AuditRecord;
import acme.entities.CodeAudit;
import acme.enumerated.Mark;
import acme.roles.Auditor;

@Service
public class AuditorAuditRecordUpdateService extends AbstractService<Auditor, AuditRecord> {

	// Internal state ---------------------------------------------------------
	@Autowired
	private AuditorAuditRecordRepository repository;

	// AbstractService interface ----------------------------------------------


	@Override
	public void authorise() {

		boolean status;
		AuditRecord auditRecord;
		CodeAudit codeAudit = null;
		Principal principal;
		int id;
		int codeAuditId;

		id = super.getRequest().getData("id", int.class);
		auditRecord = this.repository.findOneAuditRecordById(id);
		codeAuditId = auditRecord.getCodeAudit().getId();

		if (auditRecord != null)
			codeAudit = this.repository.findOneCodeAuditById(codeAuditId);

		principal = super.getRequest().getPrincipal();
		status = codeAudit.getAuditor().getId() == principal.getActiveRoleId() && auditRecord != null && auditRecord.isDraftMode();

		super.getResponse().setAuthorised(status);

	}

	@Override
	public void load() {
		int id;
		AuditRecord object;

		id = super.getRequest().getData("id", int.class);

		object = this.repository.findOneAuditRecordById(id);

		super.getBuffer().addData(object);
	}

	@Override
	public void bind(final AuditRecord object) {
		assert object != null;

		super.bind(object, "code", "initialPeriod", "finalPeriod", "mark", "optionalLink", "codeAudit");
	}

	@Override
	public void validate(final AuditRecord object) {
		assert object != null;

		if (!super.getBuffer().getErrors().hasErrors("code")) {

			AuditRecord auditRecordWithCodeDuplicated = this.repository.findOneAuditRecordByCode(object.getCode());

			if (auditRecordWithCodeDuplicated != null)
				super.state(auditRecordWithCodeDuplicated.getId() == object.getId(), "code", "auditor.audit-record.form.error.code");
		}

		if (!super.getBuffer().getErrors().hasErrors("finalPeriod") && object.getFinalPeriod() != null)
			super.state(MomentHelper.isAfter(object.getFinalPeriod(), object.getInitialPeriod()), "finalPeriod", "auditor.audit-record.form.error.invalidFinalPeriod");
	}

	@Override
	public void perform(final AuditRecord object) {
		assert object != null;
		this.repository.save(object);
	}

	@Override
	public void unbind(final AuditRecord object) {
		assert object != null;

		Dataset dataset;
		SelectChoices choices;
		choices = SelectChoices.from(Mark.class, object.getMark());

		dataset = super.unbind(object, "code", "initialPeriod", "finalPeriod", "mark", "optionalLink");
		dataset.put("mark", choices.getSelected().getKey());
		dataset.put("marks", choices);
		dataset.put("codeAuditId", object.getCodeAudit().getId());
		dataset.put("draftMode", object.isDraftMode());
		dataset.put("codeAudit", object.getCodeAudit().getCode());

		super.getResponse().addData(dataset);

	}
}
