package zingg.util;

import zingg.client.util.ListMap;
import zingg.hash.HashFunction;


public interface HashUtil<D,R,C,T,T1> {
    /**
	 * Use only those functions which are defined in the conf
	 * All functions exist in the registry
	 * but we return only those which are specified in the conf
	 * @param fileName
	 * @return
	 * @throws Exception
	 */
	public ListMap<T1, HashFunction<D,R,C,T,T1>> getHashFunctionList(String fileName, Object spark)
			throws Exception ;
}
