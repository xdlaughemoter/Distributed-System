import axios from 'axios'
import { IpInfo } from '../models/NodeObject'


const NAMING_URL = 'http://143.129.43.59:8081/naming'

export const getPOIs = async (): Promise<Record<string, IpInfo>> => {
    const url = NAMING_URL + '/getALlNodes'
    const response = await axios.get<Record<string, IpInfo>>(url)
    return response.data
}

// export const addPOI = async (poiData: Omit<POI, 'id'>) => {
//     const url = BUSINESS_INFO_URL + '/BIS/manager/create-poi'
//     console.log(url);
//     const response = await axios.post(url, poiData)
//     return response.data
// }

// export const deletePOI = async (uuid: string) => {
//     const url = BUSINESS_INFO_URL + '/BIS/manager/delete-poi/'+uuid
//     console.log(url);
//     const response = await axios.get(url)
//     return response.data
// }

// export const updatePOI = async (poi: POI) => {
//     const url = BUSINESS_INFO_URL + '/BIS/manager/update-poi'
//     console.log(url);
//     const response = await axios.post(url, poi)
//     return response.data
// }