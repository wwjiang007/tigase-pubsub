package tigase.pubsub.repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import tigase.component.PropertiesBeanConfigurator;
import tigase.component.exceptions.RepositoryException;
import tigase.db.DBInitException;
import tigase.db.RepositoryFactory;
import tigase.db.UserRepository;
import tigase.kernel.KernelException;
import tigase.kernel.beans.BeanFactory;
import tigase.kernel.beans.Inject;
import tigase.osgi.ModulesManagerImpl;
import tigase.pubsub.PubSubComponent;
import tigase.xmpp.BareJID;

public class PubSubDAOFactory implements BeanFactory<IPubSubDAO<?>> {

	public static Map<String, Object> getProperties(String key, Map<String, Object> props) {
		Map<String, Object> result = new HashMap<String, Object>();

		for (Entry<String, Object> entry : props.entrySet()) {
			Matcher matcher = PubSubComponent.PARAMETRIZED_PROPERTY_PATTERN.matcher(entry.getKey());

			if (matcher.find()) {
				String keyBaseName = (matcher.group(1) != null) ? matcher.group(1) : matcher.group(3);
				String keyMod = matcher.group(2);

				if (keyBaseName.equals(key)) {
					result.put(keyMod, entry.getValue());
				}
			}
		}

		return result;
	}

	@Inject
	private PropertiesBeanConfigurator configurator;

	protected final Logger log = Logger.getLogger(this.getClass().getName());

	@Inject
	private UserRepository userRepository;

	protected PubSubDAO createDAO(Map<String, Object> props) throws RepositoryException {
		final Map<String, Object> classNames = getProperties(PubSubComponent.PUBSUB_REPO_CLASS_PROP_KEY, props);
		final Map<String, Object> resUris = getProperties(PubSubComponent.PUBSUB_REPO_URL_PROP_KEY, props);
		final Map<String, Object> poolSizes = getProperties(PubSubComponent.PUBSUB_REPO_POOL_SIZE_PROP_KEY, props);
		final String default_cls_name = (String) classNames.get(null);

		PubSubDAOPool dao_pool = new PubSubDAOPool();
		dao_pool.init(null, null, userRepository);

		for (Entry<String, Object> e : resUris.entrySet()) {
			String domain = e.getKey();
			String resUri = (String) e.getValue();
			String className = classNames.containsKey(domain) ? (String) classNames.get(domain) : null;
			Class<? extends IPubSubDAO> repoClass = null;
			if (className == null) {
				try {
					repoClass = RepositoryFactory.getRepoClass(IPubSubDAO.class, resUri);
				} catch (DBInitException ex) {
					log.log(Level.FINE, "could not autodetect PubSubDAO implementation for domain = {0} for uri = {1}",
							new Object[] { (domain == null ? "default" : domain), resUri });
				}
			}
			if (repoClass == null) {
				if (className == null)
					className = default_cls_name;
				try {
					repoClass = (Class<? extends IPubSubDAO>) ModulesManagerImpl.getInstance().forName(className);
				} catch (ClassNotFoundException ex) {
					throw new RepositoryException("could not find class " + className + " to use as PubSubDAO"
							+ " implementation for domain " + (domain == null ? "default" : domain), ex);
				}
			}
			int dao_pool_size;
			Map<String, String> repoParams = new HashMap<String, String>();

			try {
				Object value = (poolSizes.containsKey(domain) ? poolSizes.get(domain) : poolSizes.get(null));
				dao_pool_size = (value instanceof Integer) ? ((Integer) value) : Integer.parseInt((String) value);
			} catch (Exception ex) {
				// we should set it at least to 10 to improve performace,
				// as previous value (1) was really not enought
				dao_pool_size = 10;
			}
			if (log.isLoggable(Level.FINER)) {
				log.finer("Creating DAO for domain=" + domain + "; class="
						+ (repoClass == null ? className : repoClass.getCanonicalName()) + "; uri=" + resUri + "; poolSize="
						+ dao_pool_size);
			}

			for (int i = 0; i < dao_pool_size; i++) {
				try {
					IPubSubDAO dao = repoClass.newInstance();
					dao.init(resUri, repoParams, userRepository);
					dao_pool.addDao(domain == null ? null : BareJID.bareJIDInstanceNS(domain), dao);
				} catch (InstantiationException ex) {
					throw new RepositoryException("Cound not create instance of " + repoClass.getCanonicalName(), ex);
				} catch (IllegalAccessException ex) {
					throw new RepositoryException("Cound not create instance of " + repoClass.getCanonicalName(), ex);
				}
			}

			if (log.isLoggable(Level.CONFIG)) {
				log.config("Registered DAO for " + ((domain == null) ? "default " : "") + "domain "
						+ ((domain == null) ? "" : domain));
			}
		}

		return dao_pool;
	}

	@Override
	public IPubSubDAO<?> createInstance() throws KernelException {
		try {
			return createDAO(configurator.getProperties());
		} catch (RepositoryException e) {
			throw new KernelException(e);
		}
	}

}
