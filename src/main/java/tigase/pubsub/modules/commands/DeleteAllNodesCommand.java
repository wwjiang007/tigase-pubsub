package tigase.pubsub.modules.commands;

import tigase.component.adhoc.AdHocCommand;
import tigase.component.adhoc.AdHocCommandException;
import tigase.component.adhoc.AdHocResponse;
import tigase.component.adhoc.AdhHocRequest;
import tigase.component.exceptions.RepositoryException;
import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;
import tigase.form.Field;
import tigase.form.Form;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.repository.IPubSubDAO;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import java.util.Arrays;

/**
 * Class description
 *
 *
 * @version 5.0.0, 2010.03.27 at 05:11:57 GMT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
@Bean(name = "deleteAllNodesCommand")
public class DeleteAllNodesCommand implements AdHocCommand {

	@Inject
	private PubSubConfig config;

	@Inject
	private IPubSubDAO<?,?> dao;

	@Inject
	private UserRepository userRepo;

	/**
	 * Method description
	 *
	 *
	 * @param request
	 * @param response
	 *
	 * @throws AdHocCommandException
	 */
	@Override
	public void execute(AdhHocRequest request, AdHocResponse response) throws AdHocCommandException {
		try {
			final Element data = request.getCommand().getChild("x", "jabber:x:data");

			if ((request.getAction() != null) && "cancel".equals(request.getAction())) {
				response.cancelSession();
			} else {
				if (data == null) {
					Form form = new Form("result", "Delete all nodes", "To DELETE ALL NODES please check checkbox.");

					form.addField(Field.fieldBoolean("tigase-pubsub#delete-all", Boolean.FALSE,
							"YES! I'm sure! I want to delete all nodes"));
					response.getElements().add(form.getElement());
					response.startSession();
				} else {
					Form form = new Form(data);

					if ("submit".equals(form.getType())) {
						final Boolean rebuild = form.getAsBoolean("tigase-pubsub#delete-all");

						if ((rebuild != null) && (rebuild.booleanValue() == true)) {
							startRemoving(request.getIq().getStanzaTo().getBareJID());

							Form f = new Form(null, "Info", "Nodes has been deleted");

							response.getElements().add(f.getElement());
						} else {
							Form f = new Form(null, "Info", "Deleting cancelled.");

							response.getElements().add(f.getElement());
						}
					}

					response.completeSession();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();

			throw new AdHocCommandException(Authorization.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public String getName() {
		return "Deleting ALL nodes";
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public String getNode() {
		return "delete-all-nodes";
	}

	@Override
	public boolean isAllowedFor(JID jid) {
		return Arrays.asList(config.getAdmins()).contains(jid.toString());
	}

	private void startRemoving(BareJID serviceJid) throws RepositoryException, UserNotFoundException, TigaseDBException {
		dao.removeAllFromRootCollection(serviceJid);
		userRepo.removeSubnode(config.getServiceBareJID(), "nodes");
	}
}
