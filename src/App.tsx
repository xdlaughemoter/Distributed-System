import './App.css'
import axios from 'axios'
import { BrowserRouter, Routes, Route} from 'react-router-dom'
import {QueryClient, QueryClientProvider} from 'react-query'
import HomePage from './pages/HomePage.tsx'
import NamingServer from './pages/NamingServerInfo.tsx'
import NodeFilesPage from './pages/NodeFilesPage.tsx'

const queryClient = new QueryClient();
axios.defaults.baseURL = import.meta.env.VITE_BACKEND_URL

function App() {
    return (
       
        <QueryClientProvider client={queryClient}>
                <BrowserRouter>
                        <Routes>
                            <Route
                                path="/"
                                element={<HomePage />}
                            />
                            <Route
                                path="/naming"
                                element={<NamingServer />}
                            />
                            <Route path="/node/:nodeName" element={<NodeFilesPage />} />
                        </Routes>
                </BrowserRouter>
        </QueryClientProvider>
        
    )
}  

export default App
