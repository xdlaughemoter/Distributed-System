import AppBar from '@mui/material/AppBar'
import Toolbar from '@mui/material/Toolbar'
import Typography from '@mui/material/Typography'
import Button from '@mui/material/Button'
import { Link } from 'react-router-dom'
import './Header.css'

const Header = () => {
    // console.log(roles);
    
    return (
        <AppBar position="static">
            <Toolbar>
                <div className='container-align'>
                    <div className="left-aligned">
                        <Button color="inherit" component={Link} to="/">
                            <Typography variant="h6">Techtopia</Typography>
                        </Button>
                    
                    
                        <Button color="inherit" component={Link} to="/naming">
                            Pois
                        </Button>
                    </div>
                </div>
            </Toolbar>
        </AppBar>
    )
}

export default Header
