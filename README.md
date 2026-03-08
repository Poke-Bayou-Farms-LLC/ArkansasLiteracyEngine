# Arkansas Literacy Engine: Sovereign Edge AI Infrastructure
**Principal Architect:** Michael Lindsey | **Organization:** Poke Bayou Farms LLC
**Project Status:** Phase 1 Pilot (Cohort A - ASUN Jonesboro)

---

## Executive Overview
The **Arkansas Literacy Engine** is a Zero-Trust, air-gapped educational ecosystem designed to provide high-performance Socratic AI tutoring to rural populations. By utilizing a localized **Edge-to-Fog topology**, the system bypasses the need for public internet, ensures 100% data residency within the host facility, and eliminates the recurring OpEx costs associated with 3rd-party cloud AI APIs.

The system is engineered to solve the "Digital Divide" in the Arkansas Delta by providing resilient, state-compliant (NIST 800-53) educational tools that remain operational during network outages.

---

##  System Architecture

The repository is structured as a monorepo containing the three primary tiers of the stack:

### 1. Android Tablet Cluster (`/android_app`)
- **Language:** Kotlin / Jetpack Compose
- **Function:** Student Kiosk interface providing offline Socratic tutoring.
- **Discovery:** Uses mDNS (ZeroConf) to automatically locate and sync with the Fog Node without manual IP configuration.
- **Database:** Local SQLite (Room) for high-availability offline persistence.

### 2. Python Fog Node (`/fog_node`)
- **Language:** Python 3.14 (FastAPI)
- **Function:** The localized "Brain" of the classroom. It handles curriculum distribution, real-time telemetry sync, and GPU-accelerated inference.
- **Networking:** Asynchronous Zeroconf broadcasting to maintain the air-gapped mesh.

### 3. Fleet Command Dashboard (`/admin_dashboard`)
- **Language:** Streamlit / Plotly
- **Function:** Administrative oversight for instructors and state auditors.
- **Capabilities:** Real-time heat maps of student "friction points" and automated LACES/NRS-compliant CSV exports for state reporting.

---

##  Deployment Protocol

### Infrastructure Requirements
- **Server:** Dell PowerEdge R760 or equivalent (Optimized for NVIDIA DGX Spark integration).
- **Network:** Dedicated 200GbE Spectrum fabric for internal cluster communication.
- **Endpoints:** 30x Managed Android Tablets (API 34+).

### Local Prototyping
To initialize the localized environment for testing:

1. **Start the Fog Node:**
   ```bash
   cd fog_node
   python fog_node.py
Launch Fleet Command:

Bash
streamlit run admin_dashboard/dashboard.py
Deploy Endpoints:
Compile and install the Kotlin source to the tablet fleet. Ensure all devices are on the same localized subnet.

🔒 Security & Data Sovereignty
NIST 800-53 Compliance: All student interaction data is stored and processed locally on the physical appliance.

Zero-Trust Networking: The system uses isolated subnets; student tablets have no path to the public internet, mitigating 100% of external cyber-threat vectors.

Air-Gap Reliability: Designed for 100% uptime in environments with zero cellular or broadband availability.

Intellectual Property
© 2026 Poke Bayou Farms LLC. All Rights Reserved.
This infrastructure is developed for the Arkansas Economic Development Commission (AEDC) Technology Development Program.
