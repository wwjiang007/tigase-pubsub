package tigase.pubsub.repository;

import java.util.HashMap;
import java.util.Map;

import tigase.pubsub.Affiliation;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.util.JIDUtils;

public class NodeAffiliations implements IAffiliations {

	private final static String DELIMITER = ";";

	public static NodeAffiliations create(String data) {
		NodeAffiliations a = new NodeAffiliations();
		try {
			a.parse(data);
			return a;
		} catch (Exception e) {
			return new NodeAffiliations();
		}
	}

	private final Map<String, UsersAffiliation> affs = new HashMap<String, UsersAffiliation>();

	private boolean changed = false;

	@Override
	public void addAffiliation(String jid, Affiliation affiliation) {
		final String bareJid = JIDUtils.getNodeID(jid);
		UsersAffiliation a = new UsersAffiliation(bareJid, affiliation);
		affs.put(bareJid, a);
		changed = true;
	}

	@Override
	public void changeAffiliation(String jid, Affiliation affiliation) {
		final String bareJid = JIDUtils.getNodeID(jid);
		UsersAffiliation a = this.affs.get(bareJid);
		if (a != null) {
			a.setAffiliation(affiliation);
			changed = true;
		} else {
			a = new UsersAffiliation(bareJid, affiliation);
			affs.put(bareJid, a);
			changed = true;
		}
	}

	@Override
	public NodeAffiliations clone() throws CloneNotSupportedException {
		NodeAffiliations clone = new NodeAffiliations();
		for (UsersAffiliation a : this.affs.values()) {
			clone.affs.put(a.getJid(), a.clone());
		}
		clone.changed = changed;
		return clone;
	}

	@Override
	public UsersAffiliation[] getAffiliations() {
		return this.affs.values().toArray(new UsersAffiliation[] {});
	}

	@Override
	public UsersAffiliation getSubscriberAffiliation(String jid) {
		final String bareJid = JIDUtils.getNodeID(jid);
		UsersAffiliation a = this.affs.get(bareJid);
		if (a == null) {
			a = new UsersAffiliation(bareJid, Affiliation.none);
		}
		return a;
	}

	public boolean isChanged() {
		return changed;
	}

	public void parse(String data) {
		String[] tokens = data.split(DELIMITER);
		affs.clear();
		int c = 0;
		String jid = null;
		String state = null;
		for (String t : tokens) {
			if (c == 1) {
				state = t;
				++c;
			} else if (c == 0) {
				jid = t;
				++c;
			}
			if (c == 2) {
				UsersAffiliation b = new UsersAffiliation(jid, Affiliation.valueOf(state));
				affs.put(jid, b);
				jid = null;
				state = null;
				c = 0;
			}
		}

	}

	public void replaceBy(final NodeAffiliations nodeAffiliations) {
		this.changed = true;
		affs.clear();
		for (UsersAffiliation a : nodeAffiliations.affs.values()) {
			affs.put(a.getJid(), a);
		}
	}

	public void resetChangedFlag() {
		this.changed = false;
	}

	public String serialize() {
		StringBuilder sb = new StringBuilder();
		for (UsersAffiliation a : this.affs.values()) {
			if (a.getAffiliation() != Affiliation.none) {
				sb.append(a.getJid());
				sb.append(DELIMITER);
				sb.append(a.getAffiliation().name());
				sb.append(DELIMITER);
			}
		}
		return sb.toString();
	}

}