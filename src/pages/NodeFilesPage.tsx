import { useState, useEffect } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { 
    Typography, Table, TableBody, TableCell, TableContainer, 
    TableHead, TableRow, Paper, Button, CircularProgress, Alert, Box, Chip, Stack
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import Header from '../components/Header';

const PROXY_SERVER_BASE ='http://143.129.43.61:80/api/network'

function NodeFilesPage() {
    const { nodeName } = useParams<{ nodeName: string }>();
    const navigate = useNavigate();
    const location = useLocation();
    
    // We get the IP address passed from the HomePage state
    // Alternatively, you can fetch it again, but passing via state is faster.
    const nodeIp = location.state?.address;

    const [files, setFiles] = useState<string[]>([]);
    const [neighbours, setNeighbours] = useState<string[]>([]);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        // 1. Fetch Files from Naming Server
        const fetchFiles = fetch(`${PROXY_SERVER_BASE}/nodes/${nodeName}/files`)
            .then(res => res.ok ? res.json() : Promise.reject('Failed to fetch files'));

        // 2. Fetch Neighbours directly from the Node (Port 8080)
        // We only do this if we have the IP address
        const fetchNeighbours = nodeIp 
            ? fetch(`${PROXY_SERVER_BASE}/nodes/neighbours?ip=${nodeIp}`)
                .then(res => res.ok ? res.json() : Promise.reject('Failed to fetch neighbours'))
                .catch(() => ["Unknown", "Unknown"]) // Fallback if node is unreachable
            : Promise.resolve(["N/A", "N/A"]);

        Promise.all([fetchFiles, fetchNeighbours])
            .then(([fileData, neighbourData]) => {
                setFiles(fileData);
                setNeighbours(neighbourData);
                setLoading(false);
            })
            .catch(err => {
                setError(err.message);
                setLoading(false);
            });
    }, [nodeName, nodeIp]);

    const handleRemoveNode = async () => {
        if (window.confirm(`Are you sure you want to permanently remove node ${nodeName}?`)) {
            try {
                const response = await fetch(`${PROXY_SERVER_BASE}/nodes/${nodeName}`, {
                    method: 'DELETE',
                });
                if (response.ok) {
                    navigate('/naming');
                }
            } catch (err) {
                alert("Network error while trying to remove node.");
            }
        }
    };

    return (
        <div>
            <Header />
            
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 2, px: 2 }}>
                <Button variant="outlined" onClick={() => navigate('/naming')}>Back to Nodes</Button>
                <Button variant="contained" color="error" startIcon={<DeleteIcon />} onClick={handleRemoveNode}>
                    Remove Node
                </Button>
            </Box>
            
            <Box sx={{ px: 2, mt: 3 }}>
                <Typography variant="h5">Node: {nodeName}</Typography>
                <Typography variant="body2" color="textSecondary" sx={{ mb: 2 }}>IP Address: {nodeIp}</Typography>
                
                {/* Neighbours Section */}
                <Paper sx={{ p: 2, mb: 3, backgroundColor: '#fafafa' }}>
                    <Typography variant="subtitle1" gutterBottom><strong>Ring Topology Neighbours:</strong></Typography>
                    <Stack direction="row" spacing={2}>
                        <Chip label={`Previous: ${neighbours[0] || 'Loading...'}`} color="primary" variant="outlined" />
                        <Chip label={`Next: ${neighbours[1] || 'Loading...'}`} color="primary" variant="outlined" />
                    </Stack>
                </Paper>
            </Box>

            {loading && <Box sx={{ textAlign: 'center', p: 4 }}><CircularProgress /></Box>}
            {error && <Alert severity="error" sx={{ m: 2 }}>{error}</Alert>}

            {!loading && !error && (
                <TableContainer component={Paper} sx={{ maxWidth: 800, m: 2 }}>
                    <Table>
                        <TableHead>
                            <TableRow sx={{ backgroundColor: '#f5f5f5' }}>
                                <TableCell><strong>#</strong></TableCell>
                                <TableCell><strong>File Name</strong></TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {files.map((fileName, index) => (
                                <TableRow key={index}>
                                    <TableCell>{index + 1}</TableCell>
                                    <TableCell>{fileName}</TableCell>
                                </TableRow>
                            ))}
                            {files.length === 0 && (
                                <TableRow><TableCell colSpan={2} align="center">No files found.</TableCell></TableRow>
                            )}
                        </TableBody>
                    </Table>
                </TableContainer>
            )}
        </div>
    );
}

export default NodeFilesPage;