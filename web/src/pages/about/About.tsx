import React from 'react';
import mermaid from 'mermaid';
import styles from './About.module.css';
import homeStyles from '../home/Home.module.css';
import { useTheme } from '../../useTheme';

const About: React.FC = () => {
  const { theme } = useTheme();

  React.useEffect(() => {
    const isDark = theme === 'dark';
    mermaid.initialize({
      startOnLoad: false,
      theme: 'base',
      themeVariables: isDark
        ? {
            background: '#1a2332',
            primaryColor: '#233246',
            primaryBorderColor: '#6a9bd1',
            primaryTextColor: '#e4e9f0',
            secondaryColor: '#212c3d',
            tertiaryColor: '#2a3648',
            lineColor: '#7e889a',
            textColor: '#e4e9f0',
            fontFamily: 'Inter, Segoe UI, sans-serif',
          }
        : {
            background: '#f5f7fa',
            primaryColor: '#e6eef5',
            primaryBorderColor: '#2c5282',
            primaryTextColor: '#1a2332',
            secondaryColor: '#eaeef3',
            tertiaryColor: '#ffffff',
            lineColor: '#6b7785',
            textColor: '#1a2332',
            fontFamily: 'Inter, Segoe UI, sans-serif',
          },
    });
    const nodes = document.querySelectorAll('.mermaid');
    nodes.forEach((node) => {
      const el = node as HTMLElement;
      if (el.dataset.source) {
        el.innerHTML = el.dataset.source;
      } else {
        el.dataset.source = el.innerHTML;
      }
      el.removeAttribute('data-processed');
    });
    mermaid.run();
  }, [theme]);

  return (
    <div className={styles.container}>
      <h1>System Overview</h1>
      <p className={styles.lead}>
        The <strong>AI Agentic Mobile Tester Agent</strong> automates Android application testing with{' '}
        <strong>AI-powered agents</strong>.
      </p>

      <div className={homeStyles.mainLayout}>
        {/* Left Column: Project Cards */}
        <div className={homeStyles.leftPanel}>
          <div className={homeStyles.panelTitle}>
            <span className="icon">📦</span> Core
          </div>
          <div>
            <article className={styles.card}>
              <h3>⚙️ Backend AI Agent</h3>
              <p>
                The core service powering automated mobile testing. Built with{' '}
                <code className={styles.inline}>Koog</code> for agent orchestration
                and <code className={styles.inline}>Ktor</code> as the HTTP server.
              </p>
              <p>
                It accepts a test goal and steps from the frontend, then uses a
                configurable LLM (OpenRouter, Gemini, or Ollama) to interpret each
                step and execute UI actions on a connected device via{' '}
                <code className={styles.inline}>ADB</code>.
              </p>
              <p>
                Every run produces a structured report with executed steps,
                screenshots, and token usage.
              </p>
              <div className={styles.btns}>
                <a
                  className={`${homeStyles.btnAddStep} ${homeStyles.button}`}
                  href="https://github.com/maikotrindade/mobile-tester-agent"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  View Repo
                </a>
              </div>
            </article>
          </div>
        </div>

        {/* Right Column: System Overview Diagram */}
        <div className={homeStyles.rightPanel}>
          <div className={homeStyles.panelTitle}>
            <span className="icon">🗺️</span> Architecture
          </div>
          <section className={styles.diagram} aria-label="System overview diagram">
            <div className="mermaid">
              {`
graph TD
    A[Frontend] --> B[Backend API]
    B --> C[Koog Agent]
    C --> D[ADB]
    D --> E[Android Device]
    C --> G[LLM]
    C --> H[Reports]
    H --> A
`}
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}
export default About;
