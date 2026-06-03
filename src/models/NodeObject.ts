export interface IpInfo {
  address: string;
  nodeName: string;
}

// Matches Map<Integer, IpInfo>
// Note: JSON keys are always strings, even if they were Integers in Java
export interface IpNodesMap {
  [key: string]: IpInfo;
}