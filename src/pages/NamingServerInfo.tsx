import { useState, useEffect } from 'react';
import { Typography, List, ListItem, ListItemText, ListItemButton, Paper, CircularProgress, Box, TextField, Button, Divider } from '@mui/material';
import { useNavigate } from 'react-router-dom'; // Import useNavigate
import Header from '../components/Header';
import axios from 'axios';

interface IpInfo {
    address: string;
    nodeName: string;
}

type NodeMap = { [key: string]: IpInfo };

const PROXY_SERVER_BASE ='http://143.129.43.61:80/api/network'

function HomePage() {
    const [nodes, setNodes] = useState<NodeMap>({});
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);
    
    // State for the "Add Node" form
    const [newName, setNewName] = useState('');
    const [newIp, setNewIp] = useState('');
    
    const navigate = useNavigate();

    useEffect(() => {
        fetch(`${PROXY_SERVER_BASE}/nodes`)
            .then((response) => {
                if (!response.ok) throw new Error('Network response was not ok');
                return response.json();
            })
            .then((data: NodeMap) => {
                setNodes(data);
                setLoading(false);
            })
            .catch((err) => {
                setError(err.message);
                setLoading(false);
            });
    }, []);
    
    const fetchNodes = () => {
        setLoading(true);
        fetch(`${PROXY_SERVER_BASE}/nodes`)
            .then((response) => {
                if (!response.ok) throw new Error('Network response was not ok');
                return response.json();
            })
            .then((data: NodeMap) => {
                setNodes(data);
                setLoading(false);
            })
            .catch((err) => {
                setError(err.message);
                setLoading(false);
            });
    };

    useEffect(() => {
        fetchNodes();
    }, []);

    const handleAddNode = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!newName || !newIp) return alert("Please fill in both fields");

        try {
            // Matching your Java Controller: @PostMapping("/{name}/addNode/{ipadress}")
            const url = `${PROXY_SERVER_BASE}/addNode?newName=${newName}&newIp=${newIp}`;
            await axios.post(url);
            
            // Clear fields and refresh the list
            setNewName('');
            setNewIp('');
            fetchNodes(); 
            alert("Node added successfully!");
        } catch (err) {
            console.error(err);
            alert("Failed to add node. Check console for details.");
        }
    };



    return (
        <div> 
            <Header />
            
            {/* --- ADD NODE SECTION --- */}
            <Paper elevation={3} sx={{ maxWidth: 600, p: 3, my: 4 }}>
                <Typography variant="h6" gutterBottom>Register New Node</Typography>
                <Box component="form" onSubmit={handleAddNode} sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
                    <TextField 
                        label="Node Name" 
                        size="small" 
                        value={newName}
                        onChange={(e) => setNewName(e.target.value)}
                        required
                    />
                    <TextField 
                        label="IP Address" 
                        size="small" 
                        value={newIp}
                        onChange={(e) => setNewIp(e.target.value)}
                        required
                    />
                    <Button variant="contained" type="submit" color="primary">
                        Add Node
                    </Button>
                </Box>
            </Paper>

            <Divider sx={{ my: 4 }} />

            {/* --- LIST SECTION --- */}
            <Typography variant="h4" sx={{ my: 2 }}>Available Nodes</Typography>
            {!loading && !error && (
                <Paper elevation={3} sx={{ maxWidth: 600, p: 2 }}>
                    <List>
                        {Object.entries(nodes).map(([id, info]) => (
                            <ListItem key={id} disablePadding divider>
                                <ListItemButton onClick={() => navigate(`/node/${info.nodeName}`, { state: { address: info.address } })}>
                                    <ListItemText 
                                        primary={`Node: ${info.nodeName}`} 
                                        secondary={`ID: ${id} | IP: ${info.address}`} 
                                    />
                                </ListItemButton>
                            </ListItem>
                        ))}
                    </List>
                </Paper>
            )}
            {loading && <CircularProgress />}
            {error && <Typography color="error">Error: {error}</Typography>}
        </div>
    );
}

export default HomePage;