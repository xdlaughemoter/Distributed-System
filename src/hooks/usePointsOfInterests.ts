import {useMutation, useQuery, useQueryClient} from "react-query";
import {addPOI, deletePOI, getPOIs, updatePOI} from "../services/API.ts";
import { POI } from "../models/NodeObject.ts";

export function usePointsOfInterests() {
    // console.log(isAuthenticated());
    
    const {
        isLoading: isDoingGet,
        isError: isErrorGet,
        data: pois,
    } = useQuery({
        queryKey: ['pois'],
        queryFn: () => getPOIs()
    })
    

    return {
        isLoading: isDoingGet,
        isError: isErrorGet,
        pois: pois || []
    }
}

export function addPointOfInterest() {
    const queryClient = useQueryClient();
    const {
        mutate,
        isLoading: isDoingPost,
        isError: isErrorPost,
    } = useMutation((item: POI) => addPOI(item), {
        onSuccess: () => {
            queryClient.invalidateQueries(['poi'])
        }
    })

    return {
        isDoingPost: isDoingPost,
        isErrorPost: isErrorPost,
        addPOI: mutate
    }
}

export function deletePointOfInterest() {
    const queryClient = useQueryClient();
    const {
        mutate,
        isLoading: isDoingDelete,
        isError: isErrorDelete,
    } = useMutation((uuid: string) => deletePOI(uuid), {
        onSuccess: () => {
            queryClient.invalidateQueries(['poi'])
        }
    })

    return {
        isDoingDelete: isDoingDelete,
        isErrorDelete: isErrorDelete,
        deletePOI: mutate
    }
}

export function updatePointOfInterest() {
    const queryClient = useQueryClient();
    const {
        mutate,
        isLoading: isDoingPost,
        isError: isErrorPost,
    } = useMutation((item: POI) => updatePOI(item), {
        onSuccess: () => {
            queryClient.invalidateQueries(['poi'])
        }
    })

    return {
        isDoingPost: isDoingPost,
        isErrorPost: isErrorPost,
        updatePOI: mutate
    }
}
