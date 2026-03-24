import { useState } from 'react';

function App() {
  const [count, setCount] = useState(0);

  return (
    <div className="app">
      <h1>YAPPC Web Application</h1>
      <p>Welcome to YAPPC - Yet Another Platform Product Composer</p>
      <div className="card">
        <button onClick={() => setCount((count) => count + 1)}>
          count is {count}
        </button>
      </div>
    </div>
  );
}

export default App;
